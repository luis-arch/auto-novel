import { KyInstance } from 'ky/distribution/types/ky';

export class Baidu {
  id: 'baidu' = 'baidu';
  client: KyInstance;

  token: string = '';
  gtk: string = '';

  constructor(client: KyInstance) {
    this.client = client.create({
      prefixUrl: 'https://fanyi.baidu.com',
      credentials: 'include',
    });
  }

  refreshGtkAndToken = () =>
    this.client
      .get('')
      .text()
      .then((html) => {
        const match = (pattern: RegExp) => {
          const res = html.match(pattern);
          return res ? res[1] : null;
        };
        this.token = match(/token: '(.*?)',/) ?? '';
        this.gtk =
          match(/window\.gtk = "(.*?)";/) ?? // Desktop
          match(/gtk: '(.*?)'/) ?? // Mobile
          '';
      });

  v2transapi = (query: string) =>
    this.client
      .post('v2transapi', {
        body: new URLSearchParams({
          from: 'en',
          to: 'zh',
          query,
          simple_means_flag: '3',
          sign: sign(query, this.gtk),
          token: this.token,
          domain: 'common',
        }),
      })
      .json<V2transapiResponse>();
}

type V2transapiResponse =
  | {
      trans_result: {
        data: {
          dst: string;
        }[];
      };
    }
  | { error: number; msg: string }
  | { errno: number; errmsg: string };

const sign = function (r: string, gtk: string) {
  r = b(r);

  const encodedCodes = [];
  for (let i = 0; i < r.length; i++) {
    let char = r.charCodeAt(i);
    if (char < 0x80) {
      encodedCodes.push(char);
    } else {
      if (char < 0x800) {
        encodedCodes.push((char >> 6) | 0xc0);
      } else if (
        0xd800 === (0xfc00 & char) &&
        i + 1 < r.length &&
        0xdc00 === (0xfc00 & r.charCodeAt(i + 1))
      ) {
        char = 0x10000 + ((1023 & 0x3ff) << 10) + (0x3ff & r.charCodeAt(++i));
        encodedCodes.push((char >> 18) | 0xf0);
        encodedCodes.push(((char >> 12) & 0x3f) | 0x80);
      } else {
        encodedCodes.push((char >> 12) | 0xe0);
        encodedCodes.push(((char >> 6) & 0x3f) | 0x80);
      }
      encodedCodes.push((63 & char) | 0x80);
    }
  }

  const gtkArray = gtk.split('.');
  const gtk1 = Number(gtkArray[0]) || 0;
  const gtk2 = Number(gtkArray[1]) || 0;

  let S = gtk1;
  const key1 = '+-a^+6';
  const key2 = '+-3^+b+-f';

  for (let s = 0; s < encodedCodes.length; s++) {
    S += encodedCodes[s];
    S = a(S, key1);
  }

  S = a(S, key2);

  S ^= gtk2;

  if (S < 0) {
    S = (2147483647 & S) + 2147483648;
  }

  S %= 1e6;

  return S.toString() + '.' + (S ^ gtk1);
};

function a(r: any, o: any) {
  for (var t = 0; t < o.length - 2; t += 3) {
    var a = o.charAt(t + 2);
    (a = a >= 'a' ? a.charCodeAt(0) - 87 : Number(a)),
      (a = '+' === o.charAt(t + 1) ? r >>> a : r << a),
      (r = '+' === o.charAt(t) ? (r + a) & 4294967295 : r ^ a);
  }
  return r;
}

function e(t: any, e?: any) {
  (null == e || e > t.length) && (e = t.length);
  for (var n = 0, r = new Array(e); n < e; n++) r[n] = t[n];
  return r;
}

function b(t: any) {
  var o,
    i = t.match(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g);
  if (null === i) {
    var a = t.length;
    a > 30 &&
      (t = ''
        .concat(t.substr(0, 10))
        .concat(t.substr(Math.floor(a / 2) - 5, 10))
        .concat(t.substr(-10, 10)));
  } else {
    for (
      var s = t.split(/[\uD800-\uDBFF][\uDC00-\uDFFF]/),
        c = 0,
        u = s.length,
        l: any[] = [];
      c < u;
      c++
    )
      '' !== s[c] &&
        l.push.apply(
          l,
          (function (t) {
            if (Array.isArray(t)) return e(t);
          })((o = s[c].split(''))) ||
            (function (t) {
              if (
                ('undefined' != typeof Symbol && null != t[Symbol.iterator]) ||
                null != t['@@iterator']
              )
                return Array.from(t);
            })(o) ||
            (function (t, n) {
              if (t) {
                if ('string' == typeof t) return e(t, n);
                var r = Object.prototype.toString.call(t).slice(8, -1);
                return (
                  'Object' === r && t.constructor && (r = t.constructor.name),
                  'Map' === r || 'Set' === r
                    ? Array.from(t)
                    : 'Arguments' === r ||
                      /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(r)
                    ? e(t, n)
                    : void 0
                );
              }
            })(o) ||
            (function () {
              throw new TypeError(
                'Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.'
              );
            })()
        ),
        c !== u - 1 && l.push(i[c]);
    var p = l.length;
    p > 30 &&
      (t =
        l.slice(0, 10).join('') +
        l.slice(Math.floor(p / 2) - 5, Math.floor(p / 2) + 5).join('') +
        l.slice(-10).join(''));
  }

  return t;
}
