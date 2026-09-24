const buttons = [...document.querySelectorAll('[data-surface]')];
const grid = document.querySelector('#portrait-grid');
for (const button of buttons) {
  button.addEventListener('click', () => {
    const surface = button.dataset.surface;
    grid.classList.toggle('ink', surface === 'ink');
    for (const choice of buttons) {
      const active = choice === button;
      choice.classList.toggle('active', active);
      choice.setAttribute('aria-pressed', String(active));
    }
  });
}
