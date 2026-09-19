// Placeholder preset set for Wave 1 — reconcile against Group A's actual avatarKey list in Wave 2.
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
