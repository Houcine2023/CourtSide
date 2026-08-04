import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: route params arrive as @Input() on the component,
    // so a detail page never has to inject ActivatedRoute just to read an id.
    provideRouter(routes, withComponentInputBinding()),
    // Functional interceptors, registered once. Every request in the app now carries
    // the access token and transparently survives its expiry.
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
