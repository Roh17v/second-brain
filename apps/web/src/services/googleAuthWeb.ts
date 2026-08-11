export type GoogleWebUser = {
  id: string
  email: string
  name: string
  picture: string
  /** GIS ID token (legacy One Tap) — prefer accessToken for popup flow */
  idToken?: string
  /** OAuth access token from popup account chooser */
  accessToken?: string
}

type TokenClientConfig = {
  client_id: string
  scope: string
  callback: (response: TokenResponse) => void
  error_callback?: (error: { type?: string; message?: string }) => void
}

type TokenResponse = {
  access_token?: string
  error?: string
  error_description?: string
}

type TokenClient = {
  requestAccessToken: (overrideConfig?: { prompt?: string }) => void
}

type GoogleAccountsOAuth2 = {
  initTokenClient: (config: TokenClientConfig) => TokenClient
}

declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: GoogleAccountsOAuth2
      }
    }
  }
}

/**
 * Web Google sign-in via GIS OAuth popup (account chooser in a real Google window).
 * Avoids the One Tap card that renders as a white box on dark UIs.
 */
class WebGoogleAuthService {
  private clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? ''
  private isInitialized = false
  private initPromise: Promise<void> | null = null

  isConfigured(): boolean {
    return Boolean(this.clientId)
  }

  async signIn(): Promise<GoogleWebUser> {
    if (!this.clientId) {
      throw new Error(
        'Google Client ID not configured. Set VITE_GOOGLE_CLIENT_ID in apps/web/.env',
      )
    }

    await this.initializeGoogleScript()

    const oauth2 = window.google?.accounts?.oauth2
    if (!oauth2) {
      throw new Error('Google Identity Services failed to load')
    }

    return new Promise((resolve, reject) => {
      let settled = false
      const finish = (fn: () => void) => {
        if (settled) return
        settled = true
        fn()
      }

      try {
        const client = oauth2.initTokenClient({
          client_id: this.clientId,
          scope: 'openid email profile',
          callback: (response) => {
            if (response.error) {
              finish(() =>
                reject(
                  new Error(
                    response.error_description ||
                      response.error ||
                      'Google sign-in failed',
                  ),
                ),
              )
              return
            }
            if (!response.access_token) {
              finish(() =>
                reject(new Error('Google did not return an access token')),
              )
              return
            }
            finish(() =>
              resolve({
                id: '',
                email: '',
                name: '',
                picture: '',
                accessToken: response.access_token,
              }),
            )
          },
          error_callback: (error) => {
            const type = error?.type ?? ''
            if (type === 'popup_closed') {
              finish(() => reject(new Error('Google sign-in was cancelled.')))
              return
            }
            if (type === 'popup_failed_to_open') {
              finish(() =>
                reject(
                  new Error(
                    'Could not open Google sign-in. Allow popups for this site and try again.',
                  ),
                ),
              )
              return
            }
            finish(() =>
              reject(
                new Error(error?.message || 'Google sign-in failed'),
              ),
            )
          },
        })

        // Always show account chooser (clean Google UI, not One Tap card)
        client.requestAccessToken({ prompt: 'select_account' })
      } catch (err) {
        finish(() =>
          reject(
            err instanceof Error
              ? err
              : new Error('Failed to start Google sign-in'),
          ),
        )
      }
    })
  }

  private async initializeGoogleScript(): Promise<void> {
    if (this.isInitialized) return
    if (this.initPromise) return this.initPromise

    this.initPromise = new Promise((resolve, reject) => {
      if (document.querySelector('#google-identity-script')) {
        this.waitForGoogleAPI().then(resolve).catch(reject)
        return
      }

      const script = document.createElement('script')
      script.id = 'google-identity-script'
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.defer = true
      script.onload = () => {
        this.waitForGoogleAPI().then(resolve).catch(reject)
      }
      script.onerror = () => {
        reject(new Error('Failed to load Google Identity Services script'))
      }
      document.head.appendChild(script)
    })

    await this.initPromise
    this.isInitialized = true
  }

  private waitForGoogleAPI(): Promise<void> {
    return new Promise((resolve, reject) => {
      let attempts = 0
      const maxAttempts = 50
      const checkInterval = window.setInterval(() => {
        attempts++
        if (window.google?.accounts?.oauth2) {
          window.clearInterval(checkInterval)
          resolve()
        } else if (attempts >= maxAttempts) {
          window.clearInterval(checkInterval)
          reject(new Error('Google Identity Services timed out'))
        }
      }, 100)
    })
  }
}

export const webGoogleAuthService = new WebGoogleAuthService()
