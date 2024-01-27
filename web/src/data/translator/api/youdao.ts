import { AES } from 'crypto-es/lib/aes';
import { Utf8 } from 'crypto-es/lib/core';
import { MD5 } from 'crypto-es/lib/md5';
import { KyInstance } from 'ky/distribution/types/ky';
import { safeJson } from './util';

export class Youdao {
  id: 'youdao' = 'youdao';
  client: KyInstance;

  key: string = 'fsdsogkndfokasodnaso';

  constructor(client: KyInstance) {
    this.client = client.create({
      credentials: 'include',
    });
  }

  rlog = () =>
    this.client.get('https://rlogs.youdao.com/rlog.php', {
      searchParams: {
        _npid: 'fanyiweb',
        _ncat: 'pageview',
        _ncoo: (2147483647 * Math.random()).toString(),
        _nssn: 'NULL',
        _nver: '1.2.0',
        _ntms: Date.now().toString(),
      },
    });

  refreshKey = () =>
    this.client
      .get('https://dict.youdao.com/webtranslate/key', {
        searchParams: {
          keyid: 'webfanyi-key-getter',
          ...getBaseBody('asdjnjfenknafdfsdfsd'),
        },
      })
      .json()
      .then((json: any) => (this.key = json['data']['secretKey']));

  webtranslate = (query: string) =>
    this.client
      .post('https://dict.youdao.com/webtranslate', {
        body: new URLSearchParams({
          i: query,
          from: 'en',
          to: 'zh-CHS',
          dictResult: 'tru',
          keyid: 'webfanyi',
          ...getBaseBody(this.key),
        }),
        headers: {
          Accept: 'application/json, text/plain, */*',
        },
      })
      .text()
      .then(decode)
      .then(safeJson);
}

const getBaseBody = (key: string) => {
  const c = 'fanyideskweb';
  const p = 'webfanyi';
  const t = Date.now().toString();

  const sign = MD5(
    `client=${c}&mysticTime=${t}&product=${p}&key=${key}`
  ).toString();
  return {
    sign,
    client: c,
    product: p,
    appVersion: '1.0.0',
    vendor: 'web',
    pointParam: 'client,mysticTime,product',
    mysticTime: t,
    keyfrom: 'fanyi.web',
  };
};

const decode = (src: string) => {
  const dec = AES.decrypt(
    src.replace(/_/g, '/').replace(/-/g, '+'),
    MD5(
      'ydsecret://query/key/B*RGygVywfNBwpmBaZg*WT7SIOUP2T0C9WHMZN39j^DAdaZhAnxvGcCY6VYFwnHl'
    ),
    {
      iv: MD5(
        'ydsecret://query/iv/C@lZe2YzHtZ2CYgaXKSVfsb7Y4QWHjITPPZ0nQp87fBeJ!Iv6v^6fvi2WN@bYpJ4'
      ),
    }
  ).toString(Utf8);
  return dec;
};
