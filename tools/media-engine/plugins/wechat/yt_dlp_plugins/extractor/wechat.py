"""Experimental public-share extractor for WeChat Channels.

Adapted from yt-dlp pull request #17390 (Unlicense), commit
d841cddceacbc0bd770c03bbffc94aed7692a08c. This handles public share URLs;
it does not capture private in-app traffic or decrypt protected media.
"""

import json
import urllib.parse

from yt_dlp.extractor.common import InfoExtractor
from yt_dlp.utils import ExtractorError, int_or_none, traverse_obj


class WeChatIE(InfoExtractor):
    _VALID_URL = r'https?://(?:weixin\.qq\.com/sph/|channels\.weixin\.qq\.com/finder-preview/pages/sph\?id=)(?P<id>[0-9A-Za-z]+)(?:[/?#&]|$)'

    @staticmethod
    def _parse_count(count_str):
        if not count_str:
            return None
        count_str = count_str.strip()
        if count_str.endswith('万'):
            return int(float(count_str[:-1]) * 10000)
        return int_or_none(count_str)

    def _real_extract(self, url):
        video_id = self._match_id(url)
        yuanbao_res = self._download_json(
            'https://yuanbao.tencent.com/api/weixin/get_parse_result',
            video_id,
            data=json.dumps({
                'type': 'video_channel_url',
                'url': url,
                'scene': 1,
            }).encode(),
            headers={'Content-Type': 'application/json'},
            note='Extracting public WeChat Channels share',
            fatal=False,
        )
        playable_url = traverse_obj(yuanbao_res, ('data', 'playable_url'))
        if not playable_url:
            raise ExtractorError('Public WeChat Channels share is unavailable', expected=True)

        query = urllib.parse.parse_qs(urllib.parse.urlparse(playable_url).query)
        token = traverse_obj(query, ('token', 0))
        eid = traverse_obj(query, ('eid', 0))
        if not token or not eid:
            raise ExtractorError('Could not obtain the public share token', expected=True)

        feed_data = self._download_json(
            'https://channels.weixin.qq.com/finder-preview/api/feed/get_feed_info',
            video_id,
            data=json.dumps({
                'baseReq': {'generalToken': token},
                'exportId': eid,
            }).encode(),
            headers={
                'Content-Type': 'application/json',
                'Origin': 'https://channels.weixin.qq.com',
                'Referer': f'https://channels.weixin.qq.com/finder-preview/pages/feed?entry_card_type=48&comment_scene=39&appid=0&token={urllib.parse.quote(token, safe="")}&entry_scene=0&eid={urllib.parse.quote(eid, safe="")}',
            },
            note='Downloading WeChat Channels metadata',
        )
        if traverse_obj(feed_data, ('errCode')) != 0:
            raise ExtractorError(f'WeChat API error: {feed_data.get("errMsg")}', expected=True)
        if traverse_obj(feed_data, ('data', 'errMsg', 'type')) != 0:
            raise ExtractorError(
                f'WeChat API error: {traverse_obj(feed_data, ("data", "errMsg", "title"))}',
                expected=True,
            )

        feed_info = traverse_obj(feed_data, ('data', 'feedInfo')) or {}
        author_info = traverse_obj(feed_data, ('data', 'authorInfo')) or {}
        formats = []
        for codec, key in [('h264', 'h264VideoInfo'), ('h265', 'h265VideoInfo')]:
            video_url = traverse_obj(feed_info, (key, 'videoUrl'))
            if video_url:
                formats.append({
                    'url': video_url,
                    'format_id': codec,
                    'vcodec': codec,
                    'ext': 'mp4',
                })
        if not formats:
            self.raise_no_formats('No playable public WeChat Channels formats found')

        return {
            'id': video_id,
            'title': feed_info.get('description'),
            'description': feed_info.get('description'),
            'uploader': author_info.get('nickname'),
            'uploader_id': author_info.get('nickname'),
            'timestamp': feed_info.get('createtime'),
            'thumbnail': feed_info.get('coverUrl'),
            'like_count': self._parse_count(feed_info.get('likeCountFmt')),
            'repost_count': self._parse_count(feed_info.get('forwardCountFmt')),
            'comment_count': self._parse_count(feed_info.get('commentCountFmt')),
            'formats': formats,
        }
