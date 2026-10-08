export type Scenario =
  'NORMAL' | 'REVIEW' | 'DECLINE' | 'FAILOVER' | 'COMPENSATE';
export interface Payment {
  id: string;
  amountMinor: number;
  currency: 'PEN' | 'USD';
  scenario: Scenario;
}
export interface PaymentState extends Payment {
  status: string;
  attempt: number;
  risk?: string;
  acquirer?: string;
  events?: string[];
}
export interface Snapshot {
  checkpointId: string;
  nextNode: string;
  state: PaymentState;
  node?: string;
}
export interface NodeEvent {
  node: string;
  state: PaymentState;
}
export interface TimelineEvent extends NodeEvent {
  at: string;
}
