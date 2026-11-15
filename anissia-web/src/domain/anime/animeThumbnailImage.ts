/** Legacy wiki thumbnails were center-cropped. The public image retains the full composition (up to 512px). */
export function animeThumbnailImageUrl(source: string): string {
  const suffixAt = source.search(/[?#]/);
  const path = suffixAt < 0 ? source : source.slice(0, suffixAt);
  const suffix = suffixAt < 0 ? '' : source.slice(suffixAt);
  return path.replace(/(\/wiki\/[a-f0-9]{32})\.thumb\.avif$/, '$1.avif') + suffix;
}
