/// <reference types="vite/client" />
declare module '@bbob/preset-html5';
declare module '@bbob/html';

declare interface Window {
    blog: any;
    topic: any;
    bbcode: any;
    mounted: boolean;
    robotSay: (message: string) => void;
    android: Android;
    comment: Comment;
}

declare interface Comment {
    addComment: (comment: any) => void;
    refreshEmoji: (likeMap: any) => void;
    changeSort: (sort: string) => void;
}

declare interface Android {
    onPreviewImage: any;
    onReplyUser: any;
    onReplyNew: any;
    onClickUser: any;
    onNeedLogin: any;
    onClickRelated: any;
    onClickCommentSort: any;
    onClickCommentAction: (comment: string, x: number, y: number) => void;
    onCommentSort: (sort: string) => string;
    onLoadComments: (page: number, size: number, sort: string) => string;
    onToggleSmile: any;
    onLongClickText: (comment: string, x: number, y: number) => void;
}