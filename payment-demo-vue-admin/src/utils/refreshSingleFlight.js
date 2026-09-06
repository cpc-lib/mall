export function createRefreshSingleFlight(refreshFactory) {
  let inFlight = null
  return function refreshOnce() {
    if (!inFlight) inFlight = Promise.resolve().then(refreshFactory).finally(() => { inFlight = null })
    return inFlight
  }
}
