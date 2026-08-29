const DAT_CONV_VER = "2";

export default class Session {

  public id = '';
  public email = '';
  public name = '';
  public roles = [] as string[];
  public exp = 0;

  public get expired(): boolean {
    return (this.exp * 1000) <= new Date().getTime();
  }

  public get isLogin(): boolean {
    return !this.expired;
  }

  public get isRoot() {
    return this.hasRole('ROOT');
  }

  public get isAdmin() {
    return this.hasRole('ROOT', 'TRANSLATOR');
  }

  public get needRenewSession(): boolean {
    const time = (this.exp * 1000) - new Date().getTime();
    return time > 0 && time < (11 * 60000);
  }

  public hasRole(...roles: string[]) {
    if (roles.length == 0) {
      return true;
    }
    for (let i = 0 ; i < roles.length ; i++) {
      if (this.roles.indexOf(roles[i]) != -1) {
        return true;
      }
    }
    return false;
  }

  private static decodeSplitDat(dat: string): any {
    const split = dat.split('.');
    if (split.length != 5) {
        return [];
    }
    const exp = Number(split[0]);
    const base64Url = split[2];
    const id = split[3];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const payload = decodeURIComponent(atob(base64).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
    const payloadSplit = payload.split('\x1e');
    if (payloadSplit.length == 4 && payloadSplit[0] == DAT_CONV_VER) {
        return [...payloadSplit.slice(1), id, exp];
    }
    return [];
  }

  public static notLogin(): Session {
    return new Session();
  }

  public static assign(dat: string): Session {
    if (dat) {
      const split = Session.decodeSplitDat(dat);

      if (split.length) {
          const session = new Session();

          session.id = split[3];
          session.email = split[0];
          session.name = split[1];
          session.roles = (split[2] || '').split(',').filter((e: string) => e != '');
          session.exp = split[4];

          return session;
      }
    }
    return Session.notLogin();
  }
}
