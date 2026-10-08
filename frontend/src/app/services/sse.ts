export interface SseMessage {
  event: string;
  data: unknown;
}
export class SseParser {
  private pending = '';
  private readonly decoder = new TextDecoder();
  private readonly emit: (message: SseMessage) => void;
  constructor(emit: (message: SseMessage) => void) {
    this.emit = emit;
  }
  push(bytes?: Uint8Array, done = false): void {
    this.pending += this.decoder.decode(bytes, { stream: !done });
    this.pending = this.pending.replace(/\r\n/g, '\n');
    let boundary: number;
    while ((boundary = this.pending.indexOf('\n\n')) >= 0) {
      this.dispatch(this.pending.slice(0, boundary));
      this.pending = this.pending.slice(boundary + 2);
    }
    if (done && this.pending.trim()) {
      this.dispatch(this.pending);
      this.pending = '';
    }
  }
  private dispatch(frame: string): void {
    let event = 'message';
    const data: string[] = [];
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
    }
    if (data.length)
      this.emit({ event, data: JSON.parse(data.join('\n')) as unknown });
  }
}
