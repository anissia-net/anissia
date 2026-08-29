export default class LoginInfo {
  public dat = '';
  public token = '';

  public static assign(data: any): LoginInfo {
    return Object.assign(new LoginInfo(), data);
  }
}
