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
