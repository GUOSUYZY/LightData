// The heavy library is loaded only when the user starts a panorama. One job at a
// time prevents old video frames accumulating behind a slow mobile processor.
export class PanoramaEngine {
  constructor(worker) {
    this.worker = worker;
    this.sequence = 0;
    this.pending = new Map();
    this.closed = false;
    worker.onmessage = ({ data }) => {
      const task = this.pending.get(data.id);
      if (!task) return;
      this.pending.delete(data.id);
      clearTimeout(task.timeout);
      if (data.error) task.reject(new Error(data.error));
      else task.resolve(data.result);
    };
    worker.onerror = () => this.close();
    worker.onmessageerror = () => this.close();
  }

  static async create(options, timeout = 20000, signal = null) {
    if (signal?.aborted) throw new Error("Panorama preparation cancelled");
    const engine = new PanoramaEngine(new Worker(new URL("./panorama-worker.js", import.meta.url)));
    const abort = () => engine.close();
    signal?.addEventListener("abort", abort, { once: true });
    try {
      await engine.request("init", options, [], timeout);
      if (signal?.aborted) throw new Error("Panorama preparation cancelled");
      return engine;
    } catch (error) {
      engine.close();
      throw error;
    } finally {
      signal?.removeEventListener("abort", abort);
    }
  }

  request(type, payload, transfer = [], timeout = 10000) {
    if (this.closed) return Promise.reject(new Error("Panorama worker closed"));
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error("Panorama worker timed out"));
      }, timeout);
      this.pending.set(id, { resolve, reject, timeout: timer });
      try { this.worker.postMessage({ id, type, payload }, transfer); }
      catch (error) { clearTimeout(timer); this.pending.delete(id); reject(error); }
    });
  }

  submit(job) {
    // The live analysis frame remains available to freezing and normal metering.
    const data = job.frame.data.slice();
    return this.request("frame", { ...job, frame: {
      width: job.frame.width, height: job.frame.height, data,
    } }, [data.buffer]);
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    this.worker.terminate();
    for (const task of this.pending.values()) {
      clearTimeout(task.timeout);
      task.reject(new Error("Panorama worker closed"));
    }
    this.pending.clear();
  }
}
