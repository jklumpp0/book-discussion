// Matches the backend's fixed preset list exactly (see Avatars.kt).
export const AVATAR_KEYS = ['fox', 'owl', 'deer', 'bear', 'raccoon', 'hedgehog'];

const AVATAR_GLYPHS = {
  fox: '🦊',
  owl: '🦉',
  deer: '🦌',
  bear: '🐻',
  raccoon: '🦝',
  hedgehog: '🦔',
};

export const avatarGlyph = (key) => AVATAR_GLYPHS[key] ?? '🍂';
