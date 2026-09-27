"""检查仓库或解压发行包中的本地 Markdown 文档链接，避免整理后入口失效。"""
import argparse
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


def inspect(root):
    documents = sorted(set(root.glob('*.md')) | set((root / 'docs').glob('*.md'))
                       | set((root / 'acbric-mod-template').glob('README*.md')))
    errors, checked = [], 0
    for source in documents:
        text = source.read_text(encoding='utf-8-sig')
        # 示例代码中的括号不是导航；锚点不属于本检查的覆盖范围。
        text = re.sub(r'^```.*?^```[^\n]*$', '', text, flags=re.M | re.S)
        for match in re.finditer(r'\]\(([^\s)]+)(?:\s+"[^"]*")?\)', text):
            target = urlsplit(match[1].strip('<>'))
            if target.scheme or target.netloc or not target.path:
                continue
            path = unquote(target.path)
            if not path.lower().endswith('.md'):
                continue
            checked += 1
            if not (source.parent / path).is_file():
                errors.append(f'{source.relative_to(root)} -> {path}')
    if not documents:
        errors.append('No documentation found')
    return len(documents), checked, errors


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    count, checked, errors = inspect(args.root.resolve())
    if errors:
        raise SystemExit('Broken documentation links:\n' + '\n'.join(errors))
    print(f'DOCS PASS: {count} documents, {checked} local Markdown links (file targets only)')
