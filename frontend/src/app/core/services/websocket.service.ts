import { inject, Service, signal, computed, effect } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { AvailabilityEvent } from '../models/api.models';

interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body?: string;
}

@Service()
export class WebSocketService {
  private readonly auth = inject(AuthService);

  protected readonly connected = signal(false);
  protected readonly connectionError = signal<string | null>(null);

  private stompClient: any = null;
  private subscriptions = new Map<string, any>();

  connect(): void {
    if (this.stompClient?.connected) return;

    // Dynamic import of stompjs to avoid SSR issues
    import('@stomp/stompjs').then(({ Client }) => {
      const wsUrl = environment.wsUrl;

      this.stompClient = new Client({
        brokerURL: wsUrl,
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: (str: string) => console.log('[STOMP]', str),
        onConnect: () => {
          this.connected.set(true);
          this.connectionError.set(null);
          console.log('WebSocket connected');
        },
        onDisconnect: () => {
          this.connected.set(false);
          console.log('WebSocket disconnected');
        },
        onStompError: (frame: StompFrame) => {
          this.connectionError.set(frame.headers['message'] || 'WebSocket error');
          console.error('STOMP error:', frame);
        },
      });

      this.stompClient.activate();
    }).catch((err) => {
      this.connectionError.set('Failed to load WebSocket library');
      console.error('WebSocket import failed:', err);
    });
  }

  disconnect(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
      this.connected.set(false);
      this.subscriptions.clear();
    }
  }

  subscribeToClubAvailability(clubId: number, onEvent: (event: AvailabilityEvent) => void): () => void {
    if (!this.stompClient) {
      this.connect();
    }

    const destination = `/topic/clubs/${clubId}/availability`;
    const subscriptionId = `club-${clubId}`;

    // If already subscribed, don't double-subscribe
    if (this.subscriptions.has(subscriptionId)) {
      return () => this.unsubscribe(subscriptionId);
    }

    const checkAndSubscribe = () => {
      if (this.stompClient?.connected) {
        const sub = this.stompClient.subscribe(destination, (message: any) => {
          try {
            const event: AvailabilityEvent = JSON.parse(message.body);
            onEvent(event);
          } catch (err) {
            console.error('Failed to parse availability event:', err);
          }
        });
        this.subscriptions.set(subscriptionId, sub);
      } else {
        // Retry shortly
        setTimeout(checkAndSubscribe, 500);
      }
    };

    checkAndSubscribe();

    return () => this.unsubscribe(subscriptionId);
  }

  private unsubscribe(subscriptionId: string): void {
    const sub = this.subscriptions.get(subscriptionId);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(subscriptionId);
    }
  }

  isConnected(): boolean {
    return this.connected();
  }
}