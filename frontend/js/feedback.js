import { sendFeedback } from './api.js';
export function initFeedback() {
  const modal = document.getElementById('site-feedback'); const form = document.getElementById('feedback-form');
  if (!modal || !form) return;
  const status = document.getElementById('feedback-status'); let lastTrigger;
  document.querySelectorAll('[data-open-feedback]').forEach(button => button.addEventListener('click', () => {
    lastTrigger = button; status.textContent = ''; if (!modal.open) modal.showModal();
    form.querySelector('input[name="rating"]')?.focus();
  }));
  document.querySelectorAll('[data-close-feedback]').forEach(button => button.addEventListener('click', () => modal.close()));
  modal.addEventListener('close', () => { if (lastTrigger?.isConnected) lastTrigger.focus(); });
  modal.addEventListener('click', event => { if (event.target === modal) modal.close(); });
  form.addEventListener('submit', async event => {
    event.preventDefault(); status.textContent = '';
    const selected = form.querySelector('input[name="rating"]:checked');
    const comment = form.elements.namedItem('comment').value;
    if (!selected || comment.length > 500) { status.textContent = 'Select a rating and use at most 500 characters.'; return; }
    const submit = form.querySelector('[type="submit"]');submit.disabled = true;
    try {
      await sendFeedback({ rating: Number(selected.value), category: form.elements.namedItem('category').value,
        comment, website: form.elements.namedItem('website').value });
      form.reset();status.textContent = 'Thank you. Your feedback was received.';
    } catch (error) { status.textContent = error.status === 429 ? 'Too many requests. Try again later.' : 'Unable to send feedback. Please retry.'; }
    finally { submit.disabled = false; }
  });
}
