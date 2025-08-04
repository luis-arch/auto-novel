<script setup lang="ts">
import { useEventListener } from '@vueuse/core';

import { Locator } from '@/data';

const props = defineProps<{ from?: string }>();
const router = useRouter();
const authRepo = Locator.authRepository();

const authUrl = window.location.hostname.includes('fishhawk.top')
  ? 'https://auth.fishhawk.top'
  : 'https://auth.novelia.cc';

useEventListener('message', async (event: MessageEvent) => {
  if (event.origin === authUrl && event.data.type === 'login_success') {
    await authRepo.refresh().then(() => {
      const from = props.from ?? '/';
      router.replace(from);
    });
  }
});
</script>

<template>
  <iframe
    :src="authUrl + '?app=n'"
    frameborder="0"
    allowfullscreen
    style="
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      border: none;
      z-index: 9999;
    "
  ></iframe>
</template>
