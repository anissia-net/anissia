import sessionService from '../session/remote/sessionService';
import {wikiFileRequest, wikiRemote, WikiError, type WikiBody, type WikiImage, type WikiPage} from './wikiRemote';
import {wikiImageLocation, wikiImageOwnerFields, type WikiImageOwner} from './wikiImageOwner';

export const wikiRequestKinds = {
  TEMPLATE_CREATE: '템플릿 등록', TEMPLATE_DELETE: '템플릿 삭제',
  IMAGE_CREATE: '이미지 등록', IMAGE_DELETE: '이미지 삭제',
} as const;
export const wikiRequestStatuses = {PENDING: '대기', APPROVED: '승인', REJECTED: '반려', CANCELLED: '취소'} as const;
export type WikiRequestKind = keyof typeof wikiRequestKinds;
export type WikiRequestStatus = keyof typeof wikiRequestStatuses;
export interface WikiRequest {
  id: number; kind: WikiRequestKind; status: WikiRequestStatus; title: string; reason: string;
  createdBy: string; createdAt: string; processedBy: string | null; processedAt: string | null; processingReason: string;
  targetNo: number | null; imageId: string | null; body: WikiBody | null; description: string; sourceUrl: string;
  imagePreviewUrl: string | null; image: WikiImage | null;
  resultTargetNo: number | null; resultImageId: string | null; canCancel: boolean; canProcess: boolean; canApprove: boolean;
}
export interface WikiRequestInput {
  kind: WikiRequestKind; reason: string; title?: string; body?: WikiBody;
  targetNo?: number; imageId?: string; baseRevision?: number;
}
export function wikiRequestResult(item: WikiRequest): string | undefined {
  if (item.resultTargetNo) return `/wiki/templates?templateNo=${item.resultTargetNo}`;
  if (item.resultImageId) return item.image ? wikiImageLocation({...item.image, id: item.resultImageId}) : `/wiki/images?imageId=${encodeURIComponent(item.resultImageId)}`;
}
export const wikiRequestRemote = {
  list: (page = 0, status = '') => wikiRemote.request<WikiPage<WikiRequest>>(`/requests?page=${page}${status ? `&status=${encodeURIComponent(status)}` : ''}`),
  get: (id: number) => wikiRemote.request<WikiRequest>(`/requests/${id}`),
  create: (input: WikiRequestInput) => wikiRemote.request<WikiRequest>('/requests', 'POST', input),
  upload: (file: File, description: string, sourceUrl: string, reason: string, owner: WikiImageOwner, onProgress: (value: number) => void) =>
    wikiFileRequest<WikiRequest>('/requests/images', 'POST', file, {description, sourceUrl, reason, ...wikiImageOwnerFields(owner)}, onProgress),
  process: (id: number, action: 'approve' | 'reject' | 'cancel', reason = '') =>
    wikiRemote.request<WikiRequest>(`/requests/${id}/${action}`, 'POST', {reason}),
  async preview(id: number, signal?: AbortSignal): Promise<string> {
    const dat = sessionService.getDat();
    const response = await fetch(`${import.meta.env.VITE_API_PATH}/wiki/requests/${id}/image`, {
      headers: dat ? {dat} : {}, cache: 'no-store', signal,
    });
    if (!response.ok || !response.headers.get('Content-Type')?.startsWith('image/')) throw new WikiError('첨부 이미지를 불러오지 못했습니다.', response.status);
    return URL.createObjectURL(await response.blob());
  },
};
