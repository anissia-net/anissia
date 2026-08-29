export default class PageData<T> {

  public content = new Array<T>() as T[];
  public number = 0;
  public totalElements = 0;
  public totalPages = 0;
  public empty = true;
  public first = false;
  public last = false;
  public numberOfElements = 20;
  public size = 20;
  public loaded = true;

  public get next(): boolean {
    return !this.last;
  }

  public static cast<T>(object: any, mapContent: (object: any) => T = e => e): PageData<T> {
    const page: PageData<T> = Object.assign(new PageData<T>(), object);
    page.content = page.content.map(mapContent);
    return page;
  }

  public notLoaded(): PageData<T> {
    this.loaded = false;
    return this;
  }

  public static empty<T>(): PageData<T> {
    return PageData.cast<T>([], e => e);
  }

  public merge(pageData: PageData<T>): PageData<T> {
    if (this.last && pageData.empty) {
      return this;
    }
    const page: PageData<T> = Object.assign(new PageData<T>(), pageData);
    page.content = [...this.content, ...pageData.content];
    return page;
  }

}
