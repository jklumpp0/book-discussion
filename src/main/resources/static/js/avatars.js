// Matches the backend's fixed preset list exactly (see Avatars.kt).
export const AVATAR_KEYS = [
  'fox',
  'owl',
  'deer',
  'bear',
  'raccoon',
  'hedgehog',
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
  fox: '🦊',
  owl: '🦉',
  deer: '🦌',
  bear: '🐻',
  raccoon: '🦝',
  hedgehog: '🦔',
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
