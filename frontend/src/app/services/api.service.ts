import { SseParser, SseMessage } from './sse';
import { Injectable } from '@angular/core';
import { NodeEvent, Payment, Snapshot } from '../models';
@Injectable({ providedIn: 'root' })
export class ApiService {
  private headers(key: string): HeadersInit {
    return { 'Content-Type': 'application/json', 'X-Api-Key': key };
  }
  private async json<T>(path: string, key: string, body?: unknown): Promise<T> {
    const response = await fetch('/api' + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: this.headers(key),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok)
      throw new Error(
        `Operación no disponible (HTTP ${response.status}). ${response.status === 409 ? 'Se debe consultar el estado actual antes de continuar.' : ''}`,
      );
    return response.json() as Promise<T>;
  }
  state(id: string, key: string): Promise<Snapshot> {
    return this.json('/payments/' + encodeURIComponent(id), key);
  }
  history(id: string, key: string): Promise<Snapshot[]> {
    return this.json('/payments/' + encodeURIComponent(id) + '/history', key);
  }
  resume(id: string, key: string, approval: string): Promise<Snapshot> {
    return this.json('/payments/' + encodeURIComponent(id) + '/resume', key, {
      approval,
    });
  }
  retry(id: string, key: string): Promise<Snapshot> {
    return this.json('/payments/' + encodeURIComponent(id) + '/retry', key, {});
  }
  async health(): Promise<boolean> {
    try {
      return (await fetch('/api/q/health/ready')).ok;
    } catch {
      return false;
    }
  }
  async stream(
    payment: Payment,
    key: string,
    signal: AbortSignal,
    onNode: (node: NodeEvent) => void,
    onResult: (result: Snapshot) => void,
  ): Promise<void> {
    const response = await fetch('/api/payments/stream', {
      method: 'POST',
      headers: {
        ...(this.headers(key) as Record<string, string>),
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(payment),
      signal,
    });
    if (!response.ok || !response.body)
      throw new Error(
        `No se pudo iniciar el seguimiento (HTTP ${response.status}).`,
      );
    const reader = response.body.getReader();
    let completed = false;
    const parser = new SseParser((message: SseMessage) => {
      if (message.event === 'node') onNode(message.data as NodeEvent);
      if (message.event === 'result') {
        completed = true;
        onResult(message.data as Snapshot);
      }
      if (message.event === 'error')
        throw new Error(
          'Se interrumpió la ejecución. Se debe consultar el estado persistido.',
        );
    });
    try {
      while (true) {
        const chunk = await reader.read();
        parser.push(chunk.value, chunk.done);
        if (chunk.done) break;
      }
      if (!completed)
        throw new Error(
          'La conexión terminó sin resultado final. Se puede recuperar el estado guardado.',
        );
    } finally {
      reader.releaseLock();
    }
  }
}
