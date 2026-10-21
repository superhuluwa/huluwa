import { lightTheme } from 'naive-ui'

/**
 * Naive UI 浅色主题覆盖：与 styles/tokens.css 及安卓端 ui/Theme.kt 共用同一套色板
 * （松柏绿主色 + 雾白纸面 + 深墨绿正文）。安卓端是浅色 Material 3，网页端此前是暗色，
 * 这里统一切换以保证两端的按钮、输入框、弹窗、表格观感一致。
 */
export const theme = lightTheme

/** 与安卓 HuluwaColors 同名同值（便于对照检查） */
export const palette = {
    pine: '#206C54',
    pineDark: '#194F3E',
    pineSoft: '#2A785F',
    lime: '#D5E9B8',
    paper: '#F6F8F5',
    ink: '#243D32',
    muted: '#63746A',
    line: '#E3EAE0',
    softGreen: '#E6EFE2',
    danger: '#B44452',
    amber: '#956223',
    lilac: '#8260A1',
    blue: '#4E78A7'
}

export const themeOverrides = {
    common: {
        primaryColor: palette.pine,
        primaryColorHover: palette.pineSoft,
        primaryColorPressed: palette.pineDark,
        primaryColorSuppl: palette.pineSoft,
        infoColor: palette.blue,
        successColor: palette.pine,
        warningColor: palette.amber,
        errorColor: palette.danger,
        bodyColor: palette.paper,
        cardColor: '#FFFFFF',
        modalColor: '#FFFFFF',
        popoverColor: '#FFFFFF',
        tableColor: '#FFFFFF',
        inputColor: '#FFFFFF',
        borderColor: palette.line,
        dividerColor: palette.line,
        textColorBase: palette.ink,
        textColor1: palette.ink,
        textColor2: palette.muted,
        textColor3: '#80917F',
        placeholderColor: '#9AA79A',
        borderRadius: '14px',
        borderRadiusSmall: '10px',
        fontSize: '13px',
        hoverColor: '#EFF4EC'
    },
    Layout: {
        color: palette.paper,
        siderColor: palette.paper,
        headerColor: '#FFFFFF'
    },
    Menu: {
        color: 'transparent',
        itemColorHover: palette.softGreen,
        itemColorActive: palette.softGreen,
        itemColorActiveHover: palette.softGreen,
        itemTextColor: palette.muted,
        itemTextColorHover: palette.ink,
        itemTextColorActive: palette.pine,
        itemTextColorActiveHover: palette.pine,
        itemIconColor: palette.muted,
        itemIconColorActive: palette.pine
    },
    Button: {
        fontWeight: '500',
        borderRadiusTiny: '8px',
        borderRadiusSmall: '10px',
        borderRadiusMedium: '13px',
        borderRadiusLarge: '16px'
    },
    Card: {
        borderColor: palette.line,
        borderRadius: '20px'
    },
    DataTable: {
        thColor: palette.paper,
        tdColor: '#FFFFFF',
        thTextColor: palette.muted,
        borderColor: palette.line,
        borderRadius: '14px'
    },
    Input: {
        borderRadius: '14px'
    },
    Tabs: {
        tabTextColorLine: palette.muted,
        tabTextColorActiveLine: palette.pine,
        barColor: palette.pine
    },
    Tag: {
        borderRadius: '8px'
    },
    Dialog: {
        borderRadius: '20px',
        titleFontSize: '16px'
    },
    Progress: {
        railColor: palette.softGreen,
        fillColor: palette.pine
    },
    Switch: {
        railColorActive: palette.pine
    },
    Alert: {
        borderRadius: '14px'
    },
    Descriptions: {
        thColor: palette.paper,
        tdColor: '#FFFFFF',
        borderColor: palette.line
    },
    Select: {
        peers: {
            InternalSelection: {
                borderRadius: '14px'
            }
        }
    }
}
