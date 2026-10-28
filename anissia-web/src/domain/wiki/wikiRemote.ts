import sessionService from '../session/remote/sessionService';
export {wikiLocation, wikiHistoryLocation} from './wikiLocation';

export interface WikiBody { schemaVersion: 1; blocks: unknown[] }
export interface WikiAccess { canContribute: boolean; isOperator: boolean; eligibleAt?: string; message: string }
export interface WikiTemplate { templateNo: number; title: string; body: WikiBody; revision: number }
export interface WikiDocument {
  animeNo?: number;
  templateNo?: number;
  title: string;
  body: WikiBody;
  revision: number;
  updatedAt?: string;
  updatedBy?: string;
  templates: WikiTemplate[];
  deleted?: boolean;
  renderWarning?: string;
}
export interface WikiUsage { kind: 'anime' | 'template'; targetNo: number; title: string }
export interface WikiRecent extends WikiUsage { revision: number; summary: string; updatedBy: string; updatedAt: string; deleted: boolean; thumbnail?: string | null }
export interface WikiHistory { revision: number; summary: string; updatedBy: string; updatedAt: string }
export interface WikiImage {
  id: string;
  status: 'WAITING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETING' | 'DELETED' | 'RESTORING';
  revision: number;
  description: string;
  sourceUrl: string;
  url?: string;
  thumbnailUrl?: string | null;
  width?: number;
  height?: number;
  frames?: number;
  bytes?: number;
  animated?: boolean;
  error?: string;
  createdAt: string;
  updatedAt: string;
  updatedBy: string;
}
export interface WikiImageUsage extends WikiUsage { url: string; scope: 'current' | 'history'; revision: number | null }
export interface AnimeWikiImages { thumbnail: string | null; images: WikiImage[] }
export interface WikiImageUsagePage extends WikiPage<WikiImageUsage> { currentCount: number; historyCount: number; canDelete: boolean }
export interface WikiPage<T> { content: T[]; totalElements: number; page: number; size: number }

export class WikiError extends Error {
  constructor(message: string, public status = 0) { super(message); }
}

const api = import.meta.env.VITE_API_PATH;
const emptyBody = (): WikiBody => ({schemaVersion: 1, blocks: [{w: 'p', ch: []}]});

async function request<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const dat = sessionService.getDat();
  const response = await fetch(`${api}/wiki${path}`, {
    method, signal,
    headers: {...(dat ? {dat} : {}), ...(body !== undefined ? {'Content-Type': 'application/json'} : {})},
    ...(body !== undefined ? {body: JSON.stringify(body)} : {}),
  });
  let result: {code: string; data: T; message?: string};
  try { result = await response.json(); }
  catch { throw new WikiError('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status); }
  if (!response.ok || result.code !== 'ok') throw new WikiError(result.message || '요청을 처리하지 못했습니다.', response.status);
  return result.data;
}

function upload(file: File, description: string, sourceUrl: string, onProgress: (value: number) => void) {
  const xhr = new XMLHttpRequest();
  const promise = new Promise<WikiImage>((resolve, reject) => {
    xhr.open('POST', `${api}/wiki/images`);
    const dat = sessionService.getDat();
    if (dat) xhr.setRequestHeader('dat', dat);
    xhr.timeout = 180_000;
    xhr.upload.onprogress = event => { if (event.lengthComputable) onProgress(Math.round(event.loaded / event.total * 100)); };
    xhr.onerror = () => reject(new WikiError('이미지 전송에 실패했습니다. 연결을 확인해 주세요.'));
    xhr.ontimeout = () => reject(new WikiError('이미지 전송 시간이 초과되었습니다.'));
    xhr.onabort = () => reject(new DOMException('업로드를 취소했습니다.', 'AbortError'));
    xhr.onload = () => {
      try {
        const result = JSON.parse(xhr.responseText);
        if (xhr.status < 200 || xhr.status >= 300 || result.code !== 'ok') {
          reject(new WikiError(result.message || '이미지를 업로드하지 못했습니다.', xhr.status));
        } else resolve(result.data);
      } catch { reject(new WikiError('이미지 업로드 응답을 확인하지 못했습니다.', xhr.status)); }
    };
    const data = new FormData();
    data.append('file', file);
    if (description.trim()) data.append('description', description.trim());
    if (sourceUrl.trim()) data.append('sourceUrl', sourceUrl.trim());
    xhr.send(data);
  });
  return {promise, cancel: () => xhr.abort()};
}

export const wikiRemote = {
  request, emptyBody, upload,
  access: () => request<WikiAccess>('/access'),
  images: (page = 0, q = '', deleted = false) => request<WikiPage<WikiImage>>(`/images?page=${page}&q=${encodeURIComponent(q)}${deleted ? '&deleted=true' : ''}`),
  image: (id: string) => request<WikiImage>(`/images/${encodeURIComponent(id)}`),
  animeImages: (animeNo: number, signal?: AbortSignal) => request<AnimeWikiImages>(`/anime/${animeNo}/images`, 'GET', undefined, signal),
  setAnimeThumbnail: (animeNo: number, imageId: string | null) => request<AnimeWikiImages>(`/anime/${animeNo}/thumbnail`, 'PUT', {imageId}),
  templates: (page = 0, q = '', deleted = false) => request<WikiPage<WikiDocument>>(`/templates?page=${page}&q=${encodeURIComponent(q)}${deleted ? '&deleted=true' : ''}`),
};

export function wikiErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.';
}

export function wikiDate(value?: string): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}
