import { Injectable } from '@angular/core';
import { SseParser } from './sse';
export interface Credentials {
  tenant: string;
  key: string;
  reviewer: string;
}
export interface Report {
  paymentId: string;
  summary: string;
  disposition: string;
  confidence: string;
  humanReviewRequired: boolean;
  evidence: { sourceType: string; sourceRef: string; detail: string }[];
  recommendedActions: string[];
  knowledgeSources: string[];
}
export interface Run {
  id: string;
  status: string;
  conversation_id: string;
  payment_id: string;
  report?: Report;
  narrative?: string;
  nextNode?: string;
  provider: string;
}
export interface Conversation {
  id: string;
  payment_id: string;
  status: string;
}
export interface MessageRow {
  role: 'user' | 'assistant';
  content: string;
  run_id: string;
}
export interface PaymentRow {
  id: string;
  status: string;
  amount: number;
  currency: string;
}
export interface KnowledgeSource {
  id: string;
  text: string;
  source: string;
  score: number;
}
@Injectable({ providedIn: 'root' })
export class CopilotApi {
  readonly base = '/api/v1';
  headers(auth: Credentials): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      'X-Tenant-Id': auth.tenant,
      'X-Api-Key': auth.key,
      'X-Reviewer-Key': auth.reviewer,
    };
  }
  async json<T>(
    path: string,
    auth: Credentials,
    method = 'GET',
    body?: unknown,
  ): Promise<T> {
    const response = await fetch(this.base + path, {
      method,
      headers: this.headers(auth),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      let message = `No se pudo completar la operación (HTTP ${response.status}).`;
      try {
        const data = (await response.json()) as { error?: string };
        message = data.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return response.json() as Promise<T>;
  }
  async stream(
    path: string,
    auth: Credentials,
    body: unknown,
    signal: AbortSignal,
    onEvent: (type: string, data: unknown) => void,
  ): Promise<void> {
    const response = await fetch(this.base + path, {
      method: 'POST',
      headers: { ...this.headers(auth), Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal,
    });
    if (!response.ok || !response.body) {
      let detail = `El seguimiento no se inició (HTTP ${response.status}).`;
      try {
        const data = (await response.json()) as { error?: string };
        detail = data.error ?? detail;
      } catch {}
      throw new Error(detail);
    }
    let completed = false;
    const reader = response.body.getReader();
    const parser = new SseParser((message) => {
      if (message.event === 'result') completed = true;
      if (message.event === 'error')
        throw new Error((message.data as { message: string }).message);
      onEvent(message.event, message.data);
    });
    try {
      while (true) {
        const chunk = await reader.read();
        parser.push(chunk.value, chunk.done);
        if (chunk.done) break;
      }
      if (!completed)
        throw new Error(
          'Se cerró la conexión antes del resultado final. Se debe recuperar la investigación.',
        );
    } finally {
      reader.releaseLock();
    }
  }
}
