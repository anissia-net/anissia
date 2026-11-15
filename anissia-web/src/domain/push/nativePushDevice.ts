import {nativeNotificationsSupported, requestMobileNotifications} from '../../common/mobileBridge';
import {NativePushCoordinator} from './nativePushCoordinator';
import {pushRequest} from './pushDevice';

const coordinator = new NativePushCoordinator({native: requestMobileNotifications, request: pushRequest});

export const nativePushSupported = nativeNotificationsSupported;
export const nativePushStatus = () => requestMobileNotifications('status');
export const enableNativePush = (owner: string, dat: string, isCurrent: () => boolean) => coordinator.enable(owner, dat, isCurrent);
export const syncNativePush = (owner: string, dat: string, isCurrent: () => boolean) => nativePushSupported()
  ? coordinator.sync(owner, dat, isCurrent) : Promise.resolve(null);
export const clearNativePush = (dat: string) => nativePushSupported() ? coordinator.revoke(dat) : Promise.resolve();
