
window.__measure = () => {
  const pick = (sel) => document.querySelector(sel).getBoundingClientRect();
  const img = document.querySelector('[data-case="new"] .cover').getBoundingClientRect();
  const oldImg = document.querySelector('[data-case="old"] .cover').getBoundingClientRect();
  const bubble = document.querySelector('[data-case="new"] .bubble').getBoundingClientRect();
  const row = document.querySelector('[data-case="new"]').getBoundingClientRect();
  const cap = document.querySelector('[data-case="new"] .cover-caption').getBoundingClientRect();
  const avatar = document.querySelector('[data-case="new"] .avatar').getBoundingClientRect();
  return {row: row.width, avatarRight: avatar.right, bubbleLeft: bubble.left, bubbleRight: bubble.right,
    bubbleWidth: bubble.width, captionWidth: cap.width,
    newImg: [img.width, img.height], oldImg: [oldImg.width, oldImg.height],
    ratioW: img.width / oldImg.width, ratioH: img.height / oldImg.height,
    gapToStreamRight: row.right - bubble.right};
};
