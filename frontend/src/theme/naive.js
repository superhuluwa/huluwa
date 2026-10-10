import { darkTheme } from 'naive-ui'

// Naive UI 暗色主题覆盖：与 styles/tokens.css 保持同一套色板
export const theme = darkTheme

export const themeOverrides = {
    common: {
        primaryColor: '#0EA5E9',
        primaryColorHover: '#38BDF8',
        primaryColorPressed: '#0284C7',
        primaryColorSuppl: '#38BDF8',
        infoColor: '#0EA5E9',
        successColor: '#10B981',
        warningColor: '#F59E0B',
        errorColor: '#F43F5E',
        bodyColor: '#0B0F19',
        cardColor: '#141C2E',
        modalColor: '#141C2E',
        popoverColor: '#141C2E',
        tableColor: '#141C2E',
        inputColor: '#182238',
        borderColor: 'rgba(148, 163, 184, .20)',
        dividerColor: 'rgba(148, 163, 184, .12)',
        textColorBase: '#F8FAFC',
        textColor1: '#F8FAFC',
        textColor2: '#CBD5E1',
        textColor3: '#94A3B8',
        placeholderColor: '#64748B',
        borderRadius: '10px',
        fontSize: '13px'
    },
    Layout: {
        color: '#0B0F19',
        siderColor: '#0E1422',
        headerColor: 'rgba(13, 21, 38, .7)'
    },
    Menu: {
        color: 'transparent',
        itemColorHover: 'rgba(14, 165, 233, .08)',
        itemColorActive: 'rgba(14, 165, 233, .12)',
        itemColorActiveHover: 'rgba(14, 165, 233, .16)',
        itemTextColor: '#94A3B8',
        itemTextColorHover: '#F8FAFC',
        itemTextColorActive: '#38BDF8',
        itemTextColorActiveHover: '#38BDF8',
        itemIconColor: '#94A3B8',
        itemIconColorActive: '#38BDF8'
    },
    Button: {
        fontWeight: '500'
    },
    DataTable: {
        thColor: '#182238',
        tdColor: '#141C2E',
        thTextColor: '#94A3B8',
        borderColor: 'rgba(148, 163, 184, .12)'
    },
    Card: {
        borderColor: 'rgba(148, 163, 184, .12)'
    }
}