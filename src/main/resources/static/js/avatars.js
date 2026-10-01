// Matches the backend's fixed preset list exactly (see Avatars.kt).
export const AVATAR_KEYS = [
  'pumpkin',
  'ghost',
  'bat',
  'black-cat',
  'spider',
  'skull',
  'witch',
  'vampire',
  'zombie',
];

const AVATAR_GLYPHS = {
  pumpkin: '🎃',
  ghost: '👻',
  bat: '🦇',
  'black-cat': '🐈‍⬛',
  spider: '🕷️',
  skull: '💀',
  witch: '🧙‍♀️',
  vampire: '🧛',
  zombie: '🧟',
};

export const avatarGlyph = (key) => AVATAR_GLYPHS[key] ?? '🍂';
