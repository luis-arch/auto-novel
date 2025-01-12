<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { TranslateJob, useGptWorkspaceStore } from '@/data/stores/workspace';

import { computePercentage } from './components/util';

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = gptWorkspace.jobs.find((it) => !processedJobs.value.has(it.task));
  if (job !== undefined) {
    processedJobs.value.set(job.task, job);
  }
  return job;
};

const deleteJob = (task: string) => {
  if (processedJobs.value.has(task)) {
    message.error('任务被翻译器占用');
    return;
  }
  gptWorkspace.deleteJob(task);
};

const onProgressUpdated = (
  task: string,
  state:
    | { state: 'finish' }
    | { state: 'processed'; finished: number; error: number; total: number }
) => {
  if (state.state === 'finish') {
    const job = processedJobs.value.get(task)!!;
    processedJobs.value.delete(task);
    if (
      job.progress === undefined ||
      job.progress.finished < job.progress.total
    ) {
      gptWorkspace.addUncompletedJob(job as any);
    }
    gptWorkspace.deleteJob(task);
  } else {
    const job = processedJobs.value.get(task)!!;
    job.progress = {
      finished: state.finished,
      error: state.error,
      total: state.total,
    };
  }
};

const clearCache = async () => {
  await import('@/data/translator')
    .then((it) => it.createSegIndexedDbCache('gpt-seg-cache'))
    .then((it) => it.clear());
  message.success('缓存清除成功');
};
</script>

<template>
  <div class="layout-content">
    <n-h1>GPT工作区</n-h1>

    <n-p>使用说明：</n-p>
    <n-ul>
      <n-li>
        翻译任务运行在你的浏览器里面，关闭或者刷新本页面都会停止翻译。长时间挂机的话不要把本页面放在后台，防止被浏览器杀掉。
      </n-li>
      <n-li>
        启动前先在网络小说/文库小说/文件翻译页面点击排队添加任务，完成后回到之前的页面查看翻译结果。
      </n-li>
      <n-li> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-li>
      <n-li> AccessToken有效期为90天，过期请重新获取。 </n-li>
    </n-ul>

    <n-p>
      <n-space>
        <n-button @click="showCreateWorkerModal = true">
          添加GPT翻译器
        </n-button>

        <async-button @async-click="clearCache"> 删除GPT缓存 </async-button>
      </n-space>
    </n-p>

    <n-list>
      <n-list-item v-for="worker of gptWorkspace.workers" :key="worker.id">
        <gpt-worker
          :worker="worker"
          :get-next-job="getNextJob"
          @update:progress="onProgressUpdated"
        />
      </n-list-item>
    </n-list>

    <SectionHeader title="任务队列" />
    <n-empty v-if="gptWorkspace.jobs.length === 0" description="没有任务" />
    <n-table :bordered="false" style="margin-top: 16px" v-else>
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of gptWorkspace.jobs" :key="job.task">
          <td>
            <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
            <br />
            {{ job.description }}
            <template v-if="processedJobs.has(job.task)">
              <br />
              <n-progress
                :percentage="
                  computePercentage(processedJobs.get(job.task)?.progress)
                "
                style="max-width: 600px"
              />
            </template>
          </td>
          <td style="white-space: nowrap">
            <n-time :time="job.createAt" type="relative" />
            <br />
            <n-button
              type="primary"
              text
              @click="() => gptWorkspace.topJob(job)"
            >
              置顶
            </n-button>
            <n-button
              type="error"
              text
              @click="() => deleteJob(job.task)"
              style="margin-left: 8px"
            >
              删除
            </n-button>
          </td>
        </tr>
      </tbody>
    </n-table>

    <SectionHeader title="未完成任务记录">
      <n-space :wrap="false">
        <n-button @click="gptWorkspace.retryAllUncompletedJobs()">
          全部重试
        </n-button>
        <n-button @click="gptWorkspace.deleteAllUncompletedJobs()">
          清空
        </n-button>
      </n-space>
    </SectionHeader>
    <n-empty
      v-if="gptWorkspace.uncompletedJobs.length === 0"
      description="没有任务"
    />
    <n-table :bordered="false" style="margin-top: 16px" v-else>
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of gptWorkspace.uncompletedJobs" :key="job.task">
          <td>
            <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
            <br />
            {{ job.description }}
            <template v-if="job.progress">
              <br />
              总共 {{ job.progress?.total }} / 成功
              {{ job.progress?.finished }} / 失败 {{ job.progress?.error }}
            </template>
          </td>
          <td style="white-space: nowrap">
            <n-time :time="job.createAt" type="relative" />
            <br />
            <n-button
              type="primary"
              text
              @click="() => gptWorkspace.retryUncompletedJob(job)"
            >
              重新加入
            </n-button>
            <br />
            <n-button
              type="error"
              text
              @click="() => gptWorkspace.deleteUncompletedJob(job)"
            >
              删除
            </n-button>
          </td>
        </tr>
      </tbody>
    </n-table>
  </div>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
