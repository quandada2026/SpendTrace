const listEl = document.getElementById('list');
const statusEl = document.getElementById('status');
const monthTotalEl = document.getElementById('monthTotal');
const reviewCountEl = document.getElementById('reviewCount');

const fileInput = document.getElementById('fileInput');
const uploadBtn = document.getElementById('uploadBtn');
const refreshBtn = document.getElementById('refreshBtn');

const dialog = document.getElementById('editDialog');
const fAmount = document.getElementById('fAmount');
const fMerchant = document.getElementById('fMerchant');
const fCategory = document.getElementById('fCategory');
const fTime = document.getElementById('fTime');
let editingId = null;

function fmt(n) {
  if (n == null) return '—';
  return '¥' + Number(n).toFixed(2);
}
function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]),
  );
}
function thisMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

async function load() {
  statusEl.textContent = '加载中…';
  try {
    const res = await fetch('/api/entries');
    const items = await res.json();
    render(items);
    statusEl.textContent = `共 ${items.length} 笔`;
  } catch (e) {
    statusEl.textContent = '加载失败：' + e;
  }
}

function render(items) {
  const m = thisMonth();
  let monthTotal = 0;
  let review = 0;
  for (const it of items) {
    if (it.time && it.time.startsWith(m) && it.amount) monthTotal += it.amount;
    if (it.needsReview) review++;
  }
  monthTotalEl.textContent = fmt(monthTotal);
  reviewCountEl.textContent = String(review);

  if (!items.length) {
    listEl.innerHTML = '<div class="empty">暂无账目。把截图丢进监听目录，或点「手动上传截图」。</div>';
    return;
  }

  listEl.innerHTML = items
    .map((it) => {
      const tag = it.needsReview
        ? '<span class="tag warn">待核对</span>'
        : `<span class="tag">${escapeHtml(it.category)}</span>`;
      const plat = { wechat: '微信', alipay: '支付宝', bank: '银行', unknown: '其他' }[it.platform] || it.platform;
      const sub = `${plat} · ${escapeHtml(it.time || '时间未知')} · ${it.source === 'manual' ? '手动' : '自动'}`;
      return `<div class="entry ${it.needsReview ? 'review' : ''}" data-id="${it.id}">
        <div class="meta">
          <div class="merchant">${escapeHtml(it.merchant || '未知商户')}</div>
          <div class="sub">${tag}${sub}</div>
        </div>
        <div class="amt">${fmt(it.amount)}</div>
        <div class="acts">
          <button data-act="edit">修正</button>
        </div>
      </div>`;
    })
    .join('');
}

listEl.addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-act="edit"]');
  if (!btn) return;
  const id = e.target.closest('.entry').dataset.id;
  openEdit(id);
});

async function openEdit(id) {
  const res = await fetch('/api/entries');
  const items = await res.json();
  const it = items.find((x) => x.id === id);
  if (!it) return;
  editingId = id;
  fAmount.value = it.amount ?? '';
  fMerchant.value = it.merchant ?? '';
  fCategory.value = it.category;
  fTime.value = it.time ?? '';
  dialog.showModal();
}

document.getElementById('dlgCancel').onclick = () => dialog.close();
document.getElementById('dlgSave').onclick = async () => {
  const patch = {
    amount: fAmount.value === '' ? null : Number(fAmount.value),
    merchant: fMerchant.value.trim() || null,
    category: fCategory.value,
    time: fTime.value.trim() || null,
    needsReview: false,
  };
  await fetch(`/api/entries/${editingId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  });
  dialog.close();
  load();
};
document.getElementById('dlgDelete').onclick = async () => {
  if (!confirm('确认删除这笔账目？')) return;
  await fetch(`/api/entries/${editingId}`, { method: 'DELETE' });
  dialog.close();
  load();
};

uploadBtn.onclick = () => fileInput.click();
fileInput.onchange = async () => {
  const file = fileInput.files[0];
  if (!file) return;
  statusEl.textContent = '识别中…';
  const dataUrl = await new Promise((r) => {
    const fr = new FileReader();
    fr.onload = () => r(fr.result);
    fr.readAsDataURL(file);
  });
  try {
    const res = await fetch('/api/ocr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: dataUrl }),
    });
    const entry = await res.json();
    if (entry.needsReview) {
      statusEl.textContent = '已记录，但金额未识别，请在列表里「修正」。';
    } else {
      statusEl.textContent = '已自动记账：' + fmt(entry.amount);
    }
    load();
  } catch (e) {
    statusEl.textContent = '上传失败：' + e;
  }
  fileInput.value = '';
};

refreshBtn.onclick = load;
load();
