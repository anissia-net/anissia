import {useToast} from "vue-toastification";
import Result from "./Result";

class Toast {
  private toast = useToast();
  private proxy(type: string, msg?: string) {
    if (msg) {
      switch (type) {
        case 'success':
          this.toast.success(msg);
          break;
        case 'error':
          this.toast.error(msg, { timeout: 3000 });
          break;
      }
    }
  }

  public success(msg?: string) {
    this.proxy('success', msg);
  }

  public error(msg?: string) {
    this.proxy('error', msg);
  }

  public result(result?: Result<any>) {
    if (result && result?.message) {
      result.success ? this.success(result.message) : this.error(result.message);
    }
  }
}

export default new Toast();
