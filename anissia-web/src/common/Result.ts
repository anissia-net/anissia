import PageData from "./PageData";
import toast from "./toast";

export default class Result<T> {
  constructor(code= '', message = '', data: T | null = null) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public code: string;
  public message: string;
  public data: T | null;

  public static defaultMessage = '알수없는 오류입니다.\n잠시 후 다시 시도해주세요.';

  public toastErrorMessage() {
    if (!this.success) {
      toast.error(this.message || Result.defaultMessage);
    }
  }

  public get orAlert(): Promise<T> {
    return new Promise((resolve, reject) => {
      if (this.success) {
        resolve(this.data!!);
      } else {
        toast.error(this.message || Result.defaultMessage);
        reject(this.message || Result.defaultMessage);
      }
    });
  }

  public get orIgnore(): Promise<T> {
    return new Promise((resolve, reject) => {
      if (this.success) {
        resolve(this.data!!);
      } else {
        reject(this.message || Result.defaultMessage);
      }
    });
  }

  public map<R>(cast: (t: T) => R): Result<R> {
    return Result.assign({
      code: this.code,
      message: this.message,
      data: this.data != null ? cast(this.data) : null,
    });
  }

  public get success(): boolean {
    return this.code == 'ok';
  }

  public get messageNotNull(): string {
    return this.message || '알수없는 오류입니다.\n잠시 후 다시 시도해주세요.';
  }

  public static assign<T>(obj: any): Result<T> {
    return Object.assign(new Result<T>(), obj);
  }

  public static assignPage<T>(obj: any): Result<PageData<T>> {
    return Object.assign(new Result<PageData<T>>(), obj);
  }
}
