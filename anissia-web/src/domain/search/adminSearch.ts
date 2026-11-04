import sessionService from '../session/remote/sessionService';

export const categories = {
  'user-dictionary': {label: '사용자 사전', term: '등록할 단어', value: '분해 단위', hint: '‘이세계’를 한 단어로 검색하려면 ‘이세계’만 입력하세요.'},
  synonyms: {label: '동의어', term: '기준 표현', value: '동의 표현', hint: '같은 뜻으로 검색할 표현을 쉼표로 구분하세요.'},
  corrections: {label: '교정어', term: '입력 표현', value: '교정할 표현', hint: '입력 표현을 검색 전에 어떤 표현으로 바꿀지 지정하세요.'},
  stopwords: {label: '불용어', term: '제외할 단어', value: '', hint: '검색 분석에서 제외할 토큰을 입력하세요.'},
} as const;

export type Category = keyof typeof categories;
export const searchKinds = {anime: '애니·위키', board: '게시판', template: '템플릿', image: '이미지'} as const;
export type SearchKind = keyof typeof searchKinds;

export interface SearchRule {id: string; term: string; value: string}
export interface RuleList {items: SearchRule[]; total: number}
export interface Analysis {input: string; corrected: string; raw: string[]; tokens: string[]}
export interface SearchPreview {analysis: Analysis; results: {id: string; title: string}[]; total: number}
export interface Comparison {active: SearchPreview; candidate: SearchPreview}
export interface SearchState {
  revision: number;
  active: number;
  candidate: number | null;
  tested: boolean;
  busy: boolean;
  error: string | null;
  history: {version: number; createdAt: string; actor: string}[];
}

const base = import.meta.env.VITE_API_PATH + '/admin/search';

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const dat = sessionService.getDat();
  const response = await fetch(base + path, {
    method,
    headers: {...(dat && {dat}), ...(body !== undefined && {'Content-Type': 'application/json'})},
    ...(body !== undefined && {body: JSON.stringify(body)}),
    cache: 'no-store',
  });
  const result = await response.json().catch(() => null);
  if (!response.ok || result?.code !== 'ok') {
    throw new Error(result?.message || `검색 관리 요청을 완료하지 못했습니다. (HTTP ${response.status})`);
  }
  return result.data;
}

export const searchAdmin = {
  state: () => request<SearchState>('/state'),
  rules: (category: Category, q: string, page: number) => request<RuleList>(`/rules/${category}?q=${encodeURIComponent(q)}&page=${page}`),
  add: (category: Category, term: string, value: string, revision: number) =>
    request<void>(`/rules/${category}`, 'POST', {term, value, revision}),
  remove: (category: Category, id: string, revision: number) =>
    request<void>(`/rules/${category}/${encodeURIComponent(id)}?revision=${revision}`, 'DELETE'),
  analyze: (q: string) => request<Analysis>(`/analyze?q=${encodeURIComponent(q)}`),
  prepare: (revision: number) => request<void>('/prepare', 'POST', {revision}),
  test: (q: string, kind: SearchKind, version: number) => request<Comparison>('/test', 'POST', {q, kind, version}),
  publish: (version: number) => request<void>('/publish', 'POST', {version}),
  restore: (version: number, revision: number) => request<void>('/restore', 'POST', {version, revision}),
};
