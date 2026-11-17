import {MESSAGES, PATTERNS, PHRASES, UNIT_PATTERNS} from './en.js';

const STORAGE_KEY='lightdata.language';
const CJK=/[\u3400-\u9fff]/;
function initialLanguage() {
  if (typeof window==='undefined') return 'zh-CN';
  try {
    const saved=localStorage.getItem(STORAGE_KEY);
    if (saved==='en' || saved==='zh-CN') return saved;
  } catch { /* The browser language remains available when storage is blocked. */ }
  const languages=navigator.languages?.length ? navigator.languages : [navigator.language||'en'];
  return languages[0].toLowerCase().startsWith('zh') ? 'zh-CN' : 'en';
}
export const language=initialLanguage();
export const isEnglish=language==='en';
const phrases=Object.entries(PHRASES).sort((a,b)=>b[0].length-a[0].length);

/** Translate UI prose only. Persisted identifiers, device data and option values stay canonical. */
export function t(value) {
  const original=String(value??'');
  if (!isEnglish || !CJK.test(original)) return original;
  const normalized=original.trim().replace(/\s+/g,' ');
  let result=MESSAGES[normalized];
  if (result===undefined) {
    result=normalized;
    for (const [pattern,replacement] of PATTERNS) result=result.replace(pattern,replacement);
    for (const [source,english] of phrases) result=result.replaceAll(source,english);
    for (const [pattern,replacement] of UNIT_PATTERNS) result=result.replace(pattern,replacement);
    // Chinese punctuation is used in source prose independently of the technical units.
    result=result.replaceAll('，',', ').replaceAll('。','. ').replaceAll('；','; ').replaceAll('：',': ');
  }
  return original.slice(0,original.length-original.trimStart().length)+result+original.slice(original.trimEnd().length);
}

export function initializeLanguage() {
  document.documentElement.lang=language;
  const walker=document.createTreeWalker(document,NodeFilter.SHOW_TEXT);
  const nodes=[];
  while(walker.nextNode()) {
    const node=walker.currentNode;
    if (!node.parentElement?.closest('script,style,[translate="no"]') && CJK.test(node.nodeValue)) nodes.push(node);
  }
  for (const node of nodes) node.nodeValue=t(node.nodeValue);
  for (const element of document.querySelectorAll('*')) {
    for (const attribute of ['aria-label','title','placeholder','content']) {
      if (element.hasAttribute(attribute)) element.setAttribute(attribute,t(element.getAttribute(attribute)));
    }
  }
  const selector=document.getElementById('language-select');
  if (selector) {
    selector.value=language;
    selector.addEventListener('change',()=>{
      try { localStorage.setItem(STORAGE_KEY,selector.value); }
      catch { selector.value=language; return; }
      window.location.reload();
    });
  }
}
