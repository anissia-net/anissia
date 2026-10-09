import Result from "./Result";

type ToastType = 'success' | 'error';

const TIMEOUT: Record<ToastType, number> = {success: 1000, error: 3000};
const ICON: Record<ToastType, string> = {success: 'fa-solid fa-circle-check', error: 'fa-solid fa-circle-exclamation'};
const MAX = 5;

class Toast {
  private host: HTMLElement | null = null;

  private getHost(): HTMLElement {
    if (!this.host || !this.host.isConnected) {
      this.host = document.createElement('div');
      this.host.className = 'as-toast-host';
      this.host.setAttribute('aria-live', 'polite');
      document.body.appendChild(this.host);
    }
    return this.host;
  }

  private show(type: ToastType, msg?: string) {
    const text = (msg ?? '').replace(/\\n/g, '\n').replace(/\r\n?/g, '\n').trim();
    if (!text) {
      return;
    }

    const host = this.getHost();
    const el = document.createElement('div');
    el.className = `as-toast as-toast-${type}`;
    el.setAttribute('role', type == 'error' ? 'alert' : 'status');

    const icon = document.createElement('i');
    icon.className = `as-toast-icon ${ICON[type]}`;
    const body = document.createElement('div');
    body.className = 'as-toast-text';
    body.textContent = text;
    el.append(icon, body);

    let timer = 0;
    let closed = false;
    const close = () => {
      if (closed) return;
      closed = true;
      clearTimeout(timer);
      el.classList.add('is-leave');
      el.addEventListener('animationend', () => el.remove(), {once: true});
      setTimeout(() => el.remove(), 400);
    };
    const arm = () => {
      clearTimeout(timer);
      timer = window.setTimeout(close, TIMEOUT[type]);
    };

    el.addEventListener('click', close);
    el.addEventListener('mouseenter', () => clearTimeout(timer));
    el.addEventListener('mouseleave', arm);

    host.appendChild(el);
    while (host.children.length > MAX) {
      host.firstElementChild!!.remove();
    }
    arm();
  }

  public success(msg?: string) {
    this.show('success', msg);
  }

  public error(msg?: string) {
    this.show('error', msg);
  }

  public result(result?: Result<any>) {
    if (result && result?.message) {
      result.success ? this.success(result.message) : this.error(result.message);
    }
  }
}

export default new Toast();
