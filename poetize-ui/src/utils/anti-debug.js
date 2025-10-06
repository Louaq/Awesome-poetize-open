const CHECK_INTERVAL = 500;
const SIZE_THRESHOLD = 160;
const DEBUGGER_INTERVAL = 100; // debugger循环间隔

// 使用动态函数触发 debugger，避免被构建阶段移除
let triggerDebugger;
try {
  triggerDebugger = new Function('', 'debugger');
} catch (error) {
  triggerDebugger = () => {};
}

let intervalId = null;
let forceDebuggerTimer = null;
let lastKnownOpen = false;

function isLikelyDevToolsOpen() {
  if (typeof window === 'undefined') {
    return false;
  }

  // 方法1: 窗口尺寸差异检测
  const widthGap = Math.abs(window.outerWidth - window.innerWidth);
  const heightGap = Math.abs(window.outerHeight - window.innerHeight);

  if (widthGap > SIZE_THRESHOLD || heightGap > SIZE_THRESHOLD) {
    return true;
  }

  // 方法2: 控制台打印检测（最可靠的方式）
  let detected = false;
  const element = new Image();
  Object.defineProperty(element, 'id', {
    get() {
      detected = true;
      return '';
    }
  });

  try {
    const consoleRef = window['console'];
    if (consoleRef) {
      const log = consoleRef['log'];
      const clear = consoleRef['clear'];
      if (typeof log === 'function') {
        log.call(consoleRef, element);
      }
      if (typeof clear === 'function') {
        clear.call(consoleRef);
      }
    }
  } catch (error) {
    // 忽略所有控制台调用错误
  }

  return detected;
}

function scheduleDebuggerLoop({ forceImmediate = false } = {}) {
  const runDebugger = () => {
    try {
      triggerDebugger();
    } catch (error) {
      // 忽略调用 debugger 可能抛出的异常
    }
  };

  if (forceImmediate) {
    runDebugger();
  }

  if (forceDebuggerTimer) {
    return;
  }

  runDebugger();
  forceDebuggerTimer = window.setInterval(runDebugger, DEBUGGER_INTERVAL);
}

function stopDebuggerLoop() {
  if (forceDebuggerTimer) {
    clearInterval(forceDebuggerTimer);
    forceDebuggerTimer = null;
  }
}

function onPossibleDevToolsChange(isOpen) {
  lastKnownOpen = isOpen;
  if (isOpen) {
    scheduleDebuggerLoop();
  } else {
    stopDebuggerLoop();
  }
}

function checkDevTools() {
  const detected = isLikelyDevToolsOpen();
  onPossibleDevToolsChange(detected);
}

function handleKeydown(event) {
  const key = event.key?.toLowerCase();
  const forbidden = (
    key === 'f12' ||
    (event.ctrlKey && event.shiftKey && ['i', 'j', 'c', 'u'].includes(key)) ||
    (event.ctrlKey && key === 'u')
  );

  if (forbidden) {
    event.preventDefault();
    event.stopPropagation();
    scheduleDebuggerLoop();
  }
}

function handleWindowResize() {
  checkDevTools();
}

export function initAntiDebug({ enableInDev = false } = {}) {
  if (typeof window === 'undefined') {
    return () => {};
  }

  const shouldEnable = enableInDev || process.env.NODE_ENV === 'production';
  if (!shouldEnable) {
    return () => {};
  }

  if (intervalId) {
    return () => {
      clearInterval(intervalId);
      intervalId = null;
    };
  }

  // 先检测一次
  checkDevTools();
  // 然后持续检测
  intervalId = window.setInterval(checkDevTools, CHECK_INTERVAL);

  window.addEventListener('resize', handleWindowResize, true);
  window.addEventListener('focus', checkDevTools, true);
  window.addEventListener('blur', checkDevTools, true);
  window.addEventListener('keydown', handleKeydown, true);

  return () => {
    if (intervalId) {
      clearInterval(intervalId);
      intervalId = null;
    }
    stopDebuggerLoop();
    window.removeEventListener('resize', handleWindowResize, true);
    window.removeEventListener('focus', checkDevTools, true);
    window.removeEventListener('blur', checkDevTools, true);
    window.removeEventListener('keydown', handleKeydown, true);
  };
}

export default initAntiDebug;


