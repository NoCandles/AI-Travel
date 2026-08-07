/**
 * 拾路派 - 轻量级 Markdown 渲染器
 *
 * 将 AI 回复中的 Markdown 文本转成 WXML rich-text 可识别的 nodes 数组。
 * 支持：
 *   **粗体**   *斜体*   `行内代码`
 *   - 无序列表   1. 有序列表
 *   --- 分隔线
 *   普通换行
 *
 * 不实现完整 Markdown 规范（表格/标题/链接等在小程序中用 rich-text 兼容性差），
 * 仅覆盖 AI 回复最常用的格式。
 */

/**
 * 将纯文本 Markdown 转成 rich-text nodes 数组
 * @param {string} text - 原始 AI 回复文本
 * @returns {Array} - 适配 wx rich-text 的 nodes 数组
 */
function markdownToNodes(text) {
  if (!text) return [{ type: 'text', text: '' }];

  // 统一换行符
  let content = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

  const nodes = [];
  const lines = content.split('\n');

  let inList = false;        // 是否在列表块中
  let listType = '';         // 'ul' 或 'ol'
  let listIndex = 0;
  let listBuffer = [];       // 暂存列表项的 nodes

  function flushList() {
    if (listBuffer.length === 0) return;
    // 用 <view> 包裹列表，每个 item 前加符号
    const listChildren = listBuffer.map((itemNodes, i) => {
      const prefix = listType === 'ol' ? (listIndex - listBuffer.length + i + 1) + '. ' : '• ';
      const first = itemNodes[0];
      if (first && first.type === 'text') {
        itemNodes[0] = { ...first, text: prefix + first.text };
      }
      return {
        type: 'view',
        children: itemNodes
      };
    });
    nodes.push({
      type: 'view',
      children: listChildren
    });
    listBuffer = [];
    inList = false;
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // 空行 → 刷新列表 + 加个空行间距
    if (!trimmed) {
      flushList();
      nodes.push({ type: 'text', text: '\n' });
      continue;
    }

    // 分隔线 ---
    if (/^---+\s*$/.test(trimmed)) {
      flushList();
      nodes.push({
        type: 'view',
        children: [{ type: 'text', text: '────────────' }]
      });
      continue;
    }

    // 无序列表 - xxx
    const ulMatch = trimmed.match(/^[-*+]\s+(.+)/);
    if (ulMatch) {
      if (!inList || listType !== 'ul') {
        flushList();
        inList = true;
        listType = 'ul';
      }
      listBuffer.push(parseInline(ulMatch[1]));
      continue;
    }

    // 有序列表 1. xxx
    const olMatch = trimmed.match(/^(\d+)\.\s+(.+)/);
    if (olMatch) {
      if (!inList || listType !== 'ol') {
        flushList();
        inList = true;
        listType = 'ol';
        listIndex = parseInt(olMatch[1]);
      }
      listBuffer.push(parseInline(olMatch[2]));
      continue;
    }

    // 普通行
    flushList();
    const inlineNodes = parseInline(trimmed);
    nodes.push({
      type: 'view',
      children: inlineNodes
    });
  }

  flushList();

  return nodes.length > 0 ? nodes : [{ type: 'text', text: text }];
}

/**
 * 解析行内标记：**粗体** *斜体* `行内代码`
 */
function parseInline(line) {
  const nodes = [];
  // 用正则逐段解析，按 **  → *  → ` 的顺序匹配
  const parts = line.split(/(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)/);
  for (const part of parts) {
    if (!part) continue;

    // **粗体**
    const boldMatch = part.match(/^\*\*(.+)\*\*$/);
    if (boldMatch) {
      nodes.push({ type: 'text', text: boldMatch[1], bold: true });
      continue;
    }

    // *斜体*
    const italicMatch = part.match(/^\*(.+)\*$/);
    if (italicMatch) {
      nodes.push({ type: 'text', text: italicMatch[1], italic: true });
      continue;
    }

    // `行内代码`
    const codeMatch = part.match(/^`(.+)`$/);
    if (codeMatch) {
      nodes.push({
        type: 'text',
        text: codeMatch[1],
        code: true
      });
      continue;
    }

    // 纯文本（含空格和标点）
    nodes.push({ type: 'text', text: part });
  }

  return nodes;
}

module.exports = {
  markdownToNodes
};
