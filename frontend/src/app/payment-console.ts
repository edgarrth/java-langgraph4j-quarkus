import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe, DecimalPipe } from '@angular/common';
import { ApiService } from './services/api.service';
import { Payment, Scenario, Snapshot, TimelineEvent } from './models';
@Component({
  selector: 'payment-console',
  standalone: true,
  imports: [FormsModule, JsonPipe, DecimalPipe],
  templateUrl: './payment-console.html',
  styleUrl: './payment-console.css',
})
export class PaymentConsole {
  readonly api = inject(ApiService);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly online = signal(false);
  readonly sidebarOpen = signal(false);
  readonly snapshot = signal<Snapshot | null>(null);
  readonly history = signal<Snapshot[]>([]);
  readonly timeline = signal<TimelineEvent[]>([]);
  readonly selected = signal<Snapshot | null>(null);
  readonly connection = signal('Sin conexión activa');
  readonly recent = signal<string[]>(
    JSON.parse(localStorage.getItem('axiz-payment-ids') || '[]') as string[],
  );
  readonly review = computed(() => this.snapshot()?.nextNode === 'approval');
  readonly terminal = computed(() => this.snapshot()?.nextNode === '__END__');
  readonly currentState = computed(
    () => this.snapshot()?.state ?? this.timeline().at(-1)?.state,
  );
  readonly status = computed(() => this.currentState()?.status ?? 'LISTO');
  readonly scenarios: {
    value: Scenario;
    label: string;
    description: string;
  }[] = [
    {
      value: 'NORMAL',
      label: 'Pago aprobado',
      description: 'Riesgo bajo, autorización y captura.',
    },
    {
      value: 'REVIEW',
      label: 'Revisión humana',
      description: 'Se espera una decisión antes de autorizar.',
    },
    {
      value: 'DECLINE',
      label: 'Riesgo alto',
      description: 'Se rechaza el pago antes de autorizar.',
    },
    {
      value: 'FAILOVER',
      label: 'Adquirente alterno',
      description: 'Se utiliza el secundario ante indisponibilidad.',
    },
    {
      value: 'COMPENSATE',
      label: 'Compensación',
      description: 'Se anula la autorización tras fallar la captura.',
    },
  ];
  payment: Payment = this.newPayment();
  apiKey = 'local-demo-key';
  lookupId = '';
  private controller?: AbortController;
  constructor() {
    void this.api.health().then((value) => this.online.set(value));
  }
  newPayment(): Payment {
    return {
      id: 'pay-' + crypto.randomUUID().slice(0, 8),
      amountMinor: 15000,
      currency: 'PEN',
      scenario: 'NORMAL',
    };
  }
  reset(): void {
    if (this.busy()) return;
    this.payment = this.newPayment();
    this.snapshot.set(null);
    this.timeline.set([]);
    this.history.set([]);
    this.selected.set(null);
    this.error.set('');
    this.notice.set('');
  }
  choose(scenario: Scenario): void {
    this.payment.scenario = scenario;
  }
  remember(id: string): void {
    this.recent.update((ids) =>
      [id, ...ids.filter((value) => value !== id)].slice(0, 20),
    );
    localStorage.setItem('axiz-payment-ids', JSON.stringify(this.recent()));
  }
  async start(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.timeline.set([]);
    this.history.set([]);
    this.selected.set(null);
    this.snapshot.set(null);
    this.connection.set('Conectando');
    this.controller = new AbortController();
    this.remember(this.payment.id);
    try {
      await this.api.stream(
        { ...this.payment },
        this.apiKey,
        this.controller.signal,
        (node) => {
          this.connection.set('Recibiendo eventos');
          this.timeline.update((events) => [
            ...events,
            { ...node, at: new Date().toLocaleTimeString('es-PE') },
          ]);
        },
        (result) => this.snapshot.set(result),
      );
      this.connection.set('Seguimiento finalizado');
      this.history.set(await this.api.history(this.payment.id, this.apiKey));
    } catch (error) {
      this.connection.set('Conexión interrumpida');
      this.error.set(
        this.controller.signal.aborted
          ? 'Se detuvo el seguimiento local.'
          : error instanceof Error
            ? error.message
            : 'No se pudo ejecutar el pago.',
      );
      try {
        this.snapshot.set(await this.api.state(this.payment.id, this.apiKey));
        this.notice.set('Se recuperó el último estado guardado.');
      } catch {
        this.notice.set(
          'Se puede consultar el pago por su identificador cuando el servicio esté disponible.',
        );
      }
    } finally {
      this.busy.set(false);
    }
  }
  async open(id: string): Promise<void> {
    if (this.busy() || !id.trim()) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.timeline.set([]);
    this.selected.set(null);
    try {
      const result = await this.api.state(id.trim(), this.apiKey);
      this.snapshot.set(result);
      this.payment = {
        id: result.state.id,
        amountMinor: result.state.amountMinor,
        currency: result.state.currency,
        scenario: result.state.scenario,
      };
      this.history.set(await this.api.history(id.trim(), this.apiKey));
      this.remember(id.trim());
      this.sidebarOpen.set(false);
      this.connection.set('Estado recuperado');
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo consultar el pago.',
      );
    } finally {
      this.busy.set(false);
    }
  }
  async decide(approval: 'APPROVED' | 'REJECTED'): Promise<void> {
    if (!this.review() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.snapshot.set(
        await this.api.resume(this.payment.id, this.apiKey, approval),
      );
      this.history.set(await this.api.history(this.payment.id, this.apiKey));
      this.notice.set(
        approval === 'APPROVED'
          ? 'Se aprobó la revisión y continuó el procesamiento.'
          : 'Se rechazó el pago.',
      );
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : 'No se pudo resolver la revisión.',
      );
    } finally {
      this.busy.set(false);
    }
  }
  async retry(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      this.snapshot.set(await this.api.retry(this.payment.id, this.apiKey));
      this.history.set(await this.api.history(this.payment.id, this.apiKey));
    } catch (error) {
      this.error.set(
        error instanceof Error ? error.message : 'No se pudo reanudar.',
      );
    } finally {
      this.busy.set(false);
    }
  }
  disconnect(): void {
    this.controller?.abort();
    this.notice.set(
      'Se detuvo el seguimiento local. La ejecución puede continuar en el servidor; se debe consultar su estado.',
    );
  }
  label(status: string): string {
    return (
      (
        {
          LISTO: 'Listo para procesar',
          VALIDATED: 'Solicitud validada',
          DECIDED: 'Riesgo evaluado',
          AUTHORIZED: 'Pago autorizado',
          CAPTURE_FAILED: 'Falló la captura',
          UNAVAILABLE: 'Adquirente no disponible',
          RUNNING: 'En procesamiento',
          CAPTURED: 'Pago capturado',
          REJECTED: 'Pago rechazado',
          COMPENSATED: 'Autorización anulada',
          AWAITING_APPROVAL: 'Revisión pendiente',
        } as Record<string, string>
      )[status] ?? status
    );
  }
  nodeLabel(node: string): string {
    if (node.startsWith('settlement-'))
      return node.endsWith('compensate')
        ? 'Anulación compensatoria'
        : 'Captura del pago';
    return (
      (
        {
          validate: 'Validación',
          risk: 'Evaluación de riesgo',
          merchant: 'Validación del comercio',
          decision: 'Decisión de procesamiento',
          review: 'Revisión humana',
          approval: 'Aprobación',
          authorize: 'Autorización',
          settlement: 'Captura y compensación',
          capture: 'Captura',
          compensate: 'Anulación',
        } as Record<string, string>
      )[node] ??
      (node.includes('PARALLEL')
        ? 'Riesgo y comercio'
        : node === '__START__'
          ? 'Inicio del flujo'
          : node === '__END__'
            ? 'Fin del flujo'
            : node)
    );
  }
}
