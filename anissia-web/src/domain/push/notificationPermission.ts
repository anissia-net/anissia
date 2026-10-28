let pendingPermission: Promise<NotificationPermission> | null = null;

// A quiet or unanswered browser prompt may remain pending indefinitely. Keep
// one native request across retries; timing out only abandons this caller's wait.
export function requestPushPermission(): Promise<NotificationPermission> {
  if (Notification.permission !== 'default') return Promise.resolve(Notification.permission);

  if (!pendingPermission) {
    // Called synchronously by the click handler to retain the user's gesture.
    const request = Notification.requestPermission();
    pendingPermission = request;
    const completed = () => { if (pendingPermission === request) pendingPermission = null; };
    void request.then(completed, completed);
  }
  const request = pendingPermission;

  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      reject(new Error('브라우저의 알림 권한 확인을 기다리다 중단했습니다. 주소 표시줄의 알림 아이콘이나 사이트 설정에서 알림을 허용한 뒤 다시 눌러 주세요.'));
    }, 30000);
    request.then(
      permission => { window.clearTimeout(timeout); resolve(permission); },
      error => { window.clearTimeout(timeout); reject(error); },
    );
  });
}
