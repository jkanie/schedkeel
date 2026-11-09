import { TestBed } from '@angular/core/testing';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  function render(status: string) {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.badge') as HTMLElement;
  }

  it.each([
    ['SUCCEEDED', 'good', 'succeeded'],
    ['TIMED_OUT', 'bad', 'timed out'],
    ['QUEUED', 'busy', 'queued'],
    ['PAUSED', 'neutral', 'paused'],
  ])('shows %s in the %s tone', (status, tone, label) => {
    const badge = render(status);
    expect(badge.dataset['tone']).toBe(tone);
    expect(badge.textContent).toBe(label);
  });
});
