import {createNabiWith, insertLump, makeRegistry, renderStoredHtml, type Wing} from 'nabi-note';
import {noteWings} from '../../common/note';
import type {WikiBody, WikiTemplate} from './wikiRemote';
import {mountWikiTemplateMenu} from './wikiTemplateMenu';
import {mountWikiTemplateMove, moveWikiTemplate} from './wikiTemplateMove';

export function wikiWings(templates: () => WikiTemplate[] = () => [], allowTemplates = true): readonly Wing[] {
  const base = noteWings().filter(wing => !['diff', 'localHistory'].includes(wing.w)).map(wing =>
    wing.w === 'img' && wing.button ? {...wing, button: {...wing.button, action: {kind: 'host' as const}}} : wing);
  if (!allowTemplates) return base;
  const registry = makeRegistry(base);
  const template: Wing = {
    w: 'exTemplateInclude', place: 'void', noAlign: true, attrs: ['exTemplateNo'],
    repair: node => ({...node, ch: []}),
    button: {group: 'media', icon: 'table-grid', label: {ko: '템플릿 추가'}, action: {kind: 'host'}},
    commands: {moveWikiTemplate, insertWikiTemplate(doc, selection, args, env) {
      const item = templates().find(item => String(item.templateNo) === String(args.templateNo));
      if (!item) return null;
      const result = insertLump(doc, selection.focus, {w: 'exTemplateInclude', a: {exTemplateNo: String(item.templateNo)}, ch: []}, env);
      return {doc: result.doc, selection: {anchor: {...result.caret, offset: 0}, focus: result.caret}};
    }},
    toHtml(node, _children, context) {
      const item = templates().find(item => String(item.templateNo) === String(node.a?.exTemplateNo));
      const body = item ? renderStoredHtml(item.body.blocks, registry, {allowLocalUrls: false}) ?? '' : '<p>사용할 수 없는 템플릿입니다.</p>';
      const caption = context.keys ? context.wrap('span', context.escape(item?.title ?? '템플릿'), {class: 'as-wiki-template-caption'}) : '';
      return context.element('section', caption + body, {
        class: 'as-wiki-template', 'data-template-no': String(node.a?.exTemplateNo ?? ''),
        ...(context.keys ? {'data-template-editor': 'true', contenteditable: 'false', draggable: 'false', tabindex: '0', role: 'button', 'aria-label': `${item?.title ?? '템플릿'} 템플릿 수정`} : {}),
      });
    },
    attach: host => {
      const move = mountWikiTemplateMove(host);
      const menu = mountWikiTemplateMenu(host.root, templateNo => templates().some(item => String(item.templateNo) === templateNo), move.select);
      return () => { menu(); move.unmount(); };
    },
  };
  return [...base.map(wing => ({...wing,
    ...(wing.w === 'float' ? {scopedCommands: [...(wing.scopedCommands ?? []), 'insertWikiTemplate']} : {}),
    ...(wing.w === 'table' && wing.parts?.td ? {parts: {...wing.parts, td: {...wing.parts.td, scopedCommands: [...(wing.parts.td.scopedCommands ?? []), 'insertWikiTemplate']}}} : {}),
  })), template];
}

export function renderWiki(body: WikiBody, templates: WikiTemplate[] = []): string {
  try { return renderStoredHtml(body.blocks, makeRegistry(wikiWings(() => templates)), {allowLocalUrls: false}) ?? ''; }
  catch { return ''; }
}

export function normalizeWikiBlocks(body: WikiBody, templates: WikiTemplate[] = [], allowTemplates = true): unknown[] {
  const {nabi} = createNabiWith(wikiWings(() => templates, allowTemplates), {allowLocalUrls: false});
  nabi.setJson(body.blocks);
  return nabi.getJson();
}

export function expandWikiTemplates(body: WikiBody, templates: WikiTemplate[]): unknown[] {
  const sources = new Map(templates.map(template => [String(template.templateNo), template.body.blocks]));
  const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));
  function expand(node: any): any[] {
    if (typeof node === 'string' || !node || typeof node !== 'object') return [node];
    if (node.w === 'exTemplateInclude') return copy(sources.get(String(node.a?.exTemplateNo)) ?? [{w: 'p', ch: ['사용할 수 없는 템플릿입니다.']}]);
    if (node.w === 'p' && node.ch?.some((child: any) => child?.w === 'exTemplateInclude')) {
      const output: any[] = [];
      let inline: any[] = [];
      const flush = () => { if (inline.length) output.push({...copy(node), ch: inline}); inline = []; };
      for (const child of node.ch) {
        if (child?.w === 'exTemplateInclude') { flush(); output.push(...expand(child)); }
        else inline.push(...expand(child));
      }
      flush();
      return output;
    }
    return [{...copy(node), ch: Array.isArray(node.ch) ? node.ch.flatMap(expand) : []}];
  }
  return body.blocks.flatMap(expand);
}
