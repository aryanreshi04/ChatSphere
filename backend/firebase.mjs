import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';

const projectId = process.env.FIREBASE_PROJECT_ID;
if (!projectId || projectId.includes('replace-with')) throw new Error('Set FIREBASE_PROJECT_ID in backend/.env');
const emulator = !!(process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIRESTORE_EMULATOR_HOST);
if (emulator && (!projectId.startsWith('demo-') || !process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST)) {
  throw new Error('Emulator mode requires a demo- project and both local emulator hosts. Never mix emulators with production.');
}
if (emulator && [process.env.FIREBASE_AUTH_EMULATOR_HOST, process.env.FIRESTORE_EMULATOR_HOST].some(host=>! /^(127\.0\.0\.1|localhost):[0-9]{2,5}$/.test(host))) {
  throw new Error('Emulator hosts must be local loopback addresses.');
}
const app = initializeApp(emulator ? {projectId} : {projectId, credential:applicationDefault()});
export const auth = getAuth(app);
export const firestore = getFirestore(app);
export const messaging = getMessaging(app);
export const emulatorMode = emulator;
