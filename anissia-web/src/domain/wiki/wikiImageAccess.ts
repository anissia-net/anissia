import type {WikiAccess} from './wikiRemote';

export function wikiImageUploadMode(access?: Pick<WikiAccess, 'canUploadImage' | 'canRequestImageUpload'>): 'direct' | 'request' | undefined {
  if (access?.canUploadImage === true) return 'direct';
  if (access?.canRequestImageUpload === true) return 'request';
}
