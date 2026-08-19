// 서버가 개인 ack 채널(/topic/parties/{id}/personal/{clientRequestId}) 구독을 정규 UUID로만
// 허용하므로(추측 가능한 clientRequestId로 남의 participantToken을 수신하는 것을 막기 위함)
// clientRequestId는 반드시 UUID v4 형식이어야 한다.
export function uuidv4() {
  const hex = '0123456789abcdef';
  let out = '';
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) {
      out += '-';
    } else if (i === 14) {
      out += '4';
    } else if (i === 19) {
      out += hex[((Math.random() * 4) | 0) + 8];
    } else {
      out += hex[(Math.random() * 16) | 0];
    }
  }
  return out;
}

export function encodeFrame(command, headers, body) {
  let frame = command + '\n';
  for (const key in headers) {
    frame += `${key}:${headers[key]}\n`;
  }
  frame += '\n';
  frame += body || '';
  frame += '\0';
  return frame;
}

export function parseFrames(raw) {
  return raw
    .split('\0')
    .map((chunk) => chunk.replace(/^\n+/, ''))
    .filter((chunk) => chunk.trim().length > 0)
    .map((chunk) => {
      const lines = chunk.split('\n');
      const command = lines[0];
      const headers = {};
      let i = 1;
      for (; i < lines.length; i++) {
        if (lines[i] === '') {
          i++;
          break;
        }
        const idx = lines[i].indexOf(':');
        headers[lines[i].slice(0, idx)] = lines[i].slice(idx + 1);
      }
      const body = lines.slice(i).join('\n');
      return { command, headers, body };
    });
}
