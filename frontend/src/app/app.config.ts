import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: route params arrive as @Input() on the component,
    // so a detail page never has to inject ActivatedRoute just to read an id.
    provideRouter(routes, withComponentInputBinding()),
    // Functional interceptors, registered once. Every request in the app now carries
    // the access token and transparently survives its expiry.
    provideHttpClient(withInterceptors([authInterceptor])),
    // withEventReplay() is not optional. The server ships a rendered page, but it
    // is inert until Angular hydrates it, and hydration takes a few hundred
    // milliseconds. Without replay, anything a user does in that window - typing
    // a name, tapping a button - is thrown away, and the form then complains the
    // field is empty. Replaying those events makes the hydrated page behave as if
    // it had been interactive all along.
    provideClientHydration(withEventReplay()),
  ],
};
