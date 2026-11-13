import asyncio
from dataclasses import dataclass
import logging
from typing import List
from sqlalchemy.orm import Session
from backend.database import SessionLocal

logger = logging.getLogger(__name__)

@dataclass
class BufferedReportItem:
    phone_number: int
    device_hash: str
    category: str
    call_duration: int | None
    client_ip: str | None = None

class WriteBuffer:
    """
    High-performance in-memory write buffer for SQLite.
    Prevents 'database is locked' errors by aggregating incoming crowd reports
    into batch transactions executed every 2.0s or when batch reaches 50 items.
    """
    def __init__(self, max_batch_size: int = 50, flush_interval: float = 2.0):
        self.queue: asyncio.Queue[BufferedReportItem] = asyncio.Queue()
        self.max_batch_size = max_batch_size
        self.flush_interval = flush_interval
        self._running = False
        self._task: asyncio.Task | None = None

    def enqueue(self, item: BufferedReportItem) -> bool:
        """Enqueue item into memory buffer without database blocking."""
        try:
            self.queue.put_nowait(item)
            return True
        except asyncio.QueueFull:
            return False

    def is_running(self) -> bool:
        return self._running

    def pending_count(self) -> int:
        return self.queue.qsize()

    async def flush(self, session: Session | None = None) -> int:
        """
        Drain all pending items from queue and process them in a single database transaction.
        Returns number of items processed.
        """
        if self.queue.empty():
            return 0

        items: List[BufferedReportItem] = []
        while not self.queue.empty() and len(items) < self.max_batch_size:
            try:
                items.append(self.queue.get_nowait())
                self.queue.task_done()
            except asyncio.QueueEmpty:
                break

        if not items:
            return 0

        from backend.clustering import evaluate_report

        close_session = False
        if session is None:
            session = SessionLocal()
            close_session = True

        processed_count = 0
        try:
            for it in items:
                try:
                    evaluate_report(
                        phone_number=it.phone_number,
                        device_hash=it.device_hash,
                        category=it.category,
                        call_duration=it.call_duration,
                        client_ip=it.client_ip,
                        session=session,
                    )
                    processed_count += 1
                except Exception as ex:
                    logger.error(f"Error evaluating buffered report for {it.phone_number}: {ex}")
            session.commit()
            logger.debug(f"Flushed {processed_count} buffered reports to database.")
            return processed_count
        except Exception as e:
            session.rollback()
            logger.error(f"WriteBuffer flush transaction failed: {e}", exc_info=True)
            return 0
        finally:
            if close_session:
                session.close()

    async def _flush_loop(self):
        logger.info(f"WriteBuffer worker started (flush interval: {self.flush_interval}s, batch size: {self.max_batch_size})")
        while self._running:
            try:
                await asyncio.sleep(self.flush_interval)
                await self.flush()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in WriteBuffer flush loop: {e}", exc_info=True)

    def start(self):
        if not self._running:
            self._running = True
            self._task = asyncio.create_task(self._flush_loop())

    async def stop(self):
        if self._running:
            self._running = False
            if self._task:
                self._task.cancel()
                try:
                    await self._task
                except asyncio.CancelledError:
                    pass
            # Final flush before shutdown
            await self.flush()
            logger.info("WriteBuffer worker stopped.")

write_buffer = WriteBuffer()
