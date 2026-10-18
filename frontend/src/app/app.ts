import {
  Component,
  computed,
  inject,
  signal,
  effect,
  HostListener,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe, JsonPipe } from '@angular/common';
import {
  CopilotApi,
  Credentials,
  Conversation,
  PaymentRow,
  Run,
  Report,
  KnowledgeSource,
  MessageRow,
} from './services/copilot-api.service';
import { PaymentConsole } from './payment-console';
interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  runId?: string;
  report?: Report;
  sources?: KnowledgeSource[];
  status?: string;
}
interface Trace {
  type: string;
  name: string;
  status: string;
  duration?: number;
}
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule, DecimalPipe, JsonPipe, PaymentConsole],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class AppComponent {
  readonly api = inject(CopilotApi);
  readonly messages = signal<ChatMessage[]>([]);
  readonly conversations = signal<Conversation[]>([]);
  readonly payments = signal<PaymentRow[]>([]);
  readonly runtime = signal({ provider: '', chatModel: '', version: '' });
  readonly busy = signal(false);
  readonly connected = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly traces = signal<Trace[]>([]);
  readonly activeRun = signal<Run | null>(null);
  readonly traceOpen = signal(false);
  readonly sidebarOpen = signal(false);
  readonly legacy = signal(false);
  readonly awaiting = computed(
    () => this.activeRun()?.status === 'AWAITING_REVIEW',
  );
  readonly stage = signal('Listo para investigar');
  credentials: Credentials = {
    tenant: 'tenant-pe-01',
    key: 'local-demo-key',
    reviewer: '',
  };
  paymentId = 'pay_1002';
  conversationId = 'chat-' + crypto.randomUUID().slice(0, 12);
  question = '';
  private controller?: AbortController;
  private assistantId = '';
  readonly suggestions = [
    {
      label: 'Explicar un rechazo',
      payment: 'pay_1002',
      question:
        '¿Por qué fue rechazado este pago? Se deben distinguir hechos comprobados de posibles causas.',
    },
    {
      label: 'Investigar un timeout',
      payment: 'pay_1003',
      question:
        '¿Es seguro reintentar esta captura? Revisa la secuencia de eventos y el playbook.',
    },
    {
      label: 'Comprobar liquidación',
      payment: 'pay_1004',
      question:
        '¿Este pago ya fue liquidado? Explica la diferencia entre captura y liquidación con evidencia.',
    },
  ];
  private autoFollow = true;
  @HostListener('window:scroll') onScroll(): void {
    this.autoFollow =
      document.documentElement.scrollHeight -
        window.innerHeight -
        window.scrollY <
      220;
  }
  private readonly scrollEffect = effect(() => {
    const messages = this.messages();
    if (messages.length && this.autoFollow)
      requestAnimationFrame(() =>
        window.scrollTo({
          top: document.documentElement.scrollHeight,
          behavior: 'auto',
        }),
      );
  });
  constructor() {
    void this.connect();
  }
  async connect(): Promise<void> {
    this.error.set('');
    try {
      this.runtime.set(await this.api.json('/runtime', this.credentials));
      this.payments.set(
        await this.api.json<PaymentRow[]>('/payments', this.credentials),
      );
      this.conversations.set(
        await this.api.json<Conversation[]>('/conversations', this.credentials),
      );
      this.connected.set(true);
    } catch (error) {
      this.connected.set(false);
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo conectar al servicio.',
      );
    }
  }
  reset(): void {
    if (this.busy()) return;
    this.messages.set([]);
    this.activeRun.set(null);
    this.traces.set([]);
    this.error.set('');
    this.notice.set('');
    this.conversationId = 'chat-' + crypto.randomUUID().slice(0, 12);
    this.question = '';
    this.sidebarOpen.set(false);
  }
  changePayment(): void {
    if (this.messages().length) this.reset();
  }
  suggest(index: number): void {
    const item = this.suggestions[index];
    if (!item) return;
    this.reset();
    this.paymentId = item.payment;
    this.question = item.question;
  }
  async open(conversation: Conversation): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.activeRun.set(null);
    this.traces.set([]);
    try {
      this.conversationId = conversation.id;
      this.paymentId = conversation.payment_id;
      const rows = await this.api.json<MessageRow[]>(
        '/conversations/' + encodeURIComponent(conversation.id) + '/messages',
        this.credentials,
      );
      this.messages.set(
        rows.map((m, i) => ({
          id: 'stored-' + i,
          role: m.role,
          text: m.content,
          runId: m.run_id,
        })),
      );
      if (rows.length) {
        const run = await this.api.json<Run>(
          '/runs/' + rows.at(-1)!.run_id,
          this.credentials,
        );
        this.activeRun.set(run);
        if (run.report) {
          if (rows.at(-1)?.role === 'assistant')
            this.update(rows.length - 1, { report: run.report });
          else
            this.messages.update((list) => [
              ...list,
              {
                id: crypto.randomUUID(),
                role: 'assistant',
                text: run.report!.summary,
                report: run.report,
                runId: run.id,
                status: run.status,
              },
            ]);
        }
      }
      this.sidebarOpen.set(false);
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo recuperar la conversación.',
      );
    } finally {
      this.busy.set(false);
    }
  }
  private update(index: number, patch: Partial<ChatMessage>): void {
    this.messages.update((list) =>
      list.map((m, i) => (i === index ? { ...m, ...patch } : m)),
    );
  }
  private patch(patch: Partial<ChatMessage>): void {
    this.messages.update((list) =>
      list.map((m) => (m.id === this.assistantId ? { ...m, ...patch } : m)),
    );
  }
  async send(): Promise<void> {
    if (
      this.busy() ||
      this.awaiting() ||
      !this.question.trim() ||
      !this.connected()
    )
      return;
    this.autoFollow = true;
    const text = this.question.trim();
    this.question = '';
    this.error.set('');
    this.notice.set('');
    this.traces.set([]);
    this.activeRun.set(null);
    this.assistantId = crypto.randomUUID();
    this.messages.update((list) => [
      ...list,
      { id: crypto.randomUUID(), role: 'user', text },
      { id: this.assistantId, role: 'assistant', text: '', status: 'RUNNING' },
    ]);
    await this.execute(
      '/payments/' +
        encodeURIComponent(this.paymentId) +
        '/investigations/stream',
      {
        conversationId: this.conversationId,
        requestId: crypto.randomUUID(),
        question: text,
        reviewRequested: this.forceReview,
      },
    );
  }
  forceReview = false;
  private event(type: string, data: unknown): void {
    const event = data as Record<string, unknown>;
    if (type === 'run') {
      this.patch({ runId: String(event['runId']) });
      this.activeRun.set({
        id: String(event['runId']),
        status: 'RUNNING',
        conversation_id: this.conversationId,
        payment_id: this.paymentId,
        provider: String(event['provider']),
      });
    } else if (type === 'token') {
      const old = this.messages().find((m) => m.id === this.assistantId);
      this.patch({ text: (old?.text ?? '') + String(event['text']) });
      this.stage.set('Redactando respuesta con evidencia');
    } else if (type === 'sources')
      this.patch({ sources: event['sources'] as KnowledgeSource[] });
    else if (type === 'stage' || type === 'tool') {
      const name = String(event['node'] ?? event['name']);
      const status = String(event['status']);
      this.traces.update((items) =>
        [
          ...items,
          {
            type,
            name,
            status,
            duration: event['durationMs'] as number | undefined,
          },
        ].slice(-60),
      );
      if (status === 'started') this.stage.set(this.nodeLabel(name));
    } else if (type === 'result') {
      const run = data as Run;
      this.activeRun.set(run);
      const old = this.messages().find((m) => m.id === this.assistantId);
      this.patch({
        runId: run.id,
        status: run.status,
        report: run.report,
        text:
          run.narrative ||
          old?.text ||
          run.report?.summary ||
          'Se recuperó el estado de la investigación.',
      });
      this.stage.set(
        run.status === 'AWAITING_REVIEW'
          ? 'Se requiere revisión humana'
          : 'Investigación completada',
      );
    }
  }
  private async execute(path: string, body: unknown): Promise<void> {
    this.busy.set(true);
    this.controller = new AbortController();
    try {
      await this.api.stream(
        path,
        this.credentials,
        body,
        this.controller.signal,
        (type, data) => this.event(type, data),
      );
      this.conversations.set(
        await this.api.json<Conversation[]>('/conversations', this.credentials),
      );
    } catch (error) {
      this.error.set(
        this.controller.signal.aborted
          ? 'Se detuvo el seguimiento local.'
          : error instanceof Error
            ? error.message
            : 'No se pudo completar la investigación.',
      );
      const id = this.activeRun()?.id;
      if (id) {
        try {
          const run = await this.api.json<Run>('/runs/' + id, this.credentials);
          this.activeRun.set(run);
          this.patch({ report: run.report, status: run.status });
          this.notice.set(
            'Se recuperó el último estado guardado. No se ejecutaron operaciones financieras.',
          );
        } catch {
          this.notice.set(
            'Se puede recuperar la conversación cuando el servicio esté disponible.',
          );
        }
      }
      this.patch({ status: 'INTERRUPTED' });
    } finally {
      this.busy.set(false);
    }
  }
  async review(decision: 'APPROVED' | 'REJECTED'): Promise<void> {
    const run = this.activeRun();
    if (!run || !this.credentials.reviewer || this.busy()) return;
    this.assistantId = this.messages().at(-1)?.id ?? '';
    this.patch({ text: '' });
    await this.execute('/runs/' + run.id + '/review/stream', { decision });
  }
  async retry(): Promise<void> {
    const run = this.activeRun();
    if (!run || this.busy()) return;
    this.assistantId = this.messages().at(-1)?.id ?? '';
    this.patch({ text: '' });
    await this.execute('/runs/' + run.id + '/retry/stream', {});
  }
  stop(): void {
    this.controller?.abort();
    this.notice.set(
      'Se cerró la lectura local; se debe consultar el estado antes de repetir la solicitud.',
    );
  }
  async checkpoints(): Promise<void> {
    const run = this.activeRun();
    if (!run) return;
    try {
      this.checkpointData.set(
        await this.api.json<unknown[]>(
          '/runs/' + run.id + '/checkpoints',
          this.credentials,
        ),
      );
      this.traceOpen.set(true);
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo consultar el historial.',
      );
    }
  }
  readonly checkpointData = signal<unknown[]>([]);
  keydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      void this.send();
    }
  }
  nodeLabel(node: string): string {
    return (
      (
        {
          context: 'Preparando la investigación',
          memory: 'Recuperando contexto de la conversación',
          retrieve: 'Consultando conocimiento de pagos',
          reason: 'Seleccionando herramientas de consulta',
          tools: 'Consultando evidencia operacional',
          generate_report: 'Construyendo informe estructurado',
          validate_report: 'Verificando informe y referencias',
          review_pending: 'Esperando revisión humana',
          human_review: 'Registrando decisión del revisor',
          respond: 'Redactando respuesta',
          persist: 'Guardando conversación',
          getPayment: 'Consultar pago',
          getPaymentTimeline: 'Consultar eventos del pago',
          getPaymentMerchant: 'Consultar comercio',
        } as Record<string, string>
      )[node] ?? node
    );
  }
}
