import { inject, Service, signal, computed, effect, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
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
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  protected readonly connected = signal(false);
  protected readonly connectionError = signal<string | null>(null);

  private stompClient: any = null;
  private subscriptions = new Map<string, any>();

  connect(): void {
    // The availability route is server-rendered. Node has a WebSocket global but no
    // page to resolve a relative broker URL against, so the constructor rejects with
    // "Invalid URL" and the rejection escapes as an unhandled promise rejection in the
    // prerender. Nothing to connect to until this runs in a browser.
    if (!this.isBrowser) return;
    if (this.stompClient?.connected) return;

    import('@stomp/stompjs').then((mod: any) => {
      // Node resolves stompjs to named exports; the browser gets the UMD bundle,
      // which the dev server exposes as a default object instead. Destructuring
      // the namespace directly therefore produced an undefined Client and the
      // realtime availability feed silently never connected.
      const Client = mod.Client ?? mod.default?.Client;
      if (!Client) {
        console.error('stompjs export shape:', Object.keys(mod), mod.default && Object.keys(mod.default));
        this.connectionError.set('Failed to load WebSocket library');
        return;
      }

      const wsUrl = environment.wsUrl;

      // Plain brokerURL, so stompjs builds a native WebSocket to /ws. That has to
      // match how the backend registers its endpoint: serving SockJS here used to
      // answer this raw handshake with HTTP 400, and pointing stompjs at the
      // SockJS protocol instead pulled in a CommonJS-only package that throws
      // "global is not defined" in the browser. A native WebSocket is one moving
      // part instead of two, and every browser that matters supports it.
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
    // connect() is a no-op on the server, so the retry loop below would never
    // succeed and would keep the prerender alive on a 500ms timer.
    if (!this.isBrowser) return () => {};

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