/**
 * 密码工具 - SHA256 哈希
 * 用于客户端对密码进行哈希后再传输，避免明文密码在网络上传输
 */

/**
 * 计算字符串的 SHA256 哈希值
 * @param {string} str 输入字符串
 * @returns {string} 小写十六进制哈希值
 */
function sha256(str) {
  // 将字符串转为 UTF-8 字节数组
  const bytes = stringToUtf8Bytes(str);
  // 计算 SHA256
  const hashBytes = sha256Core(bytes);
  // 转为十六进制字符串
  return bytesToHex(hashBytes);
}

/**
 * 将字符串转为 UTF-8 字节数组
 */
function stringToUtf8Bytes(str) {
  const bytes = [];
  for (let i = 0; i < str.length; i++) {
    let code = str.charCodeAt(i);
    if (code < 0x80) {
      bytes.push(code);
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code < 0xd800 || code >= 0xe000) {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      i++;
      code = 0x10000 + (((code & 0x3ff) << 10) | (str.charCodeAt(i) & 0x3ff));
      bytes.push(
        0xf0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f)
      );
    }
  }
  return bytes;
}

/**
 * SHA256 核心算法
 * @param {number[]} message 字节数组
 * @returns {number[]} 32字节哈希值
 */
function sha256Core(message) {
  // 初始哈希值（前8个素数的平方根的小数部分的前32位）
  const H = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
  ];

  // 常量 K（前64个素数的立方根的小数部分的前32位）
  const K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  // 预处理：添加 0x80，填充 0，添加长度
  const originalBitLen = message.length * 8;
  message.push(0x80);
  while ((message.length * 8) % 512 !== 448) {
    message.push(0);
  }
  for (let i = 7; i >= 0; i--) {
    message.push((originalBitLen >>> (i * 8)) & 0xff);
  }

  // 分块处理（每块 512 位 = 64 字节）
  const blocks = message.length / 64;
  for (let block = 0; block < blocks; block++) {
    const W = [];
    const offset = block * 64;

    // 将 64 字节转为 16 个 32 位字（添加 >>> 0 确保无符号）
    for (let t = 0; t < 16; t++) {
      W[t] = (((message[offset + t * 4] << 24) |
             (message[offset + t * 4 + 1] << 16) |
             (message[offset + t * 4 + 2] << 8) |
             message[offset + t * 4 + 3]) >>> 0);
    }

    // 扩展到 64 个字
    for (let t = 16; t < 64; t++) {
      const s0 = sigma0(W[t - 15]);
      const s1 = sigma1(W[t - 2]);
      W[t] = (W[t - 16] + s0 + W[t - 7] + s1) >>> 0;
    }

    // 压缩函数
    let a = H[0], b = H[1], c = H[2], d = H[3];
    let e = H[4], f = H[5], g = H[6], h = H[7];

    for (let t = 0; t < 64; t++) {
      const S1 = Sigma1(e);
      const ch = (e & f) ^ ((~e) & g);
      const temp1 = (h + S1 + ch + K[t] + W[t]) >>> 0;
      const S0 = Sigma0(a);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) >>> 0;

      h = g;
      g = f;
      f = e;
      e = (d + temp1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) >>> 0;
    }

    H[0] = (H[0] + a) >>> 0;
    H[1] = (H[1] + b) >>> 0;
    H[2] = (H[2] + c) >>> 0;
    H[3] = (H[3] + d) >>> 0;
    H[4] = (H[4] + e) >>> 0;
    H[5] = (H[5] + f) >>> 0;
    H[6] = (H[6] + g) >>> 0;
    H[7] = (H[7] + h) >>> 0;
  }

  // 将 8 个 32 位字转为 32 字节数组
  const result = [];
  for (let i = 0; i < 8; i++) {
    for (let j = 3; j >= 0; j--) {
      result.push((H[i] >>> (j * 8)) & 0xff);
    }
  }
  return result;
}

function sigma0(x) {
  return (rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3)) >>> 0;
}
function sigma1(x) {
  return (rotr(x, 17) ^ rotr(x, 19) ^ (x >>> 10)) >>> 0;
}
function Sigma0(x) {
  return (rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22)) >>> 0;
}
function Sigma1(x) {
  return (rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25)) >>> 0;
}
function rotr(x, n) {
  return ((x >>> n) | (x << (32 - n))) >>> 0;
}

/**
 * 字节数组转为小写十六进制字符串
 */
function bytesToHex(bytes) {
  const hex = [];
  for (let i = 0; i < bytes.length; i++) {
    const h = bytes[i].toString(16);
    hex.push(h.length === 1 ? '0' + h : h);
  }
  return hex.join('');
}

module.exports = { sha256 };
