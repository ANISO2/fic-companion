// Centralized ECharts brand theme — defined ONCE and reused by every chart.
// Series color order follows the project palette.
// FIC — palette « navy + or » de Carthage, alignée sur styles.css.
// Le nom interne du thème reste 'fih' (attribut theme="fih" dans 6 graphiques) :
// seules les couleurs changent, aucun renommage d'identifiant.

export const BRAND = {
  primary: '#24407e',
  accent: '#c0994b',
  warn: '#b54708',
  purple: '#6b3f6e',
  success: '#0a7c4a',
  ink: '#1a2230',
  muted: '#5f6572',
  line: '#e6e2d9'
};

export const SERIES_COLORS = ['#24407e', '#c0994b', '#b0532c', '#5f6b3a', '#6b3f6e'];

/** Register a named theme 'fih' on the shared echarts instance. */
export function registerFihTheme(echarts: { registerTheme: (name: string, theme: object) => void }): void {
  echarts.registerTheme('fih', {
    color: SERIES_COLORS,
    textStyle: { fontFamily: 'Inter, system-ui, sans-serif', color: BRAND.ink },
    title: { textStyle: { color: BRAND.ink, fontWeight: 600 } },
    legend: { textStyle: { color: BRAND.muted } },
    grid: { left: 12, right: 16, top: 28, bottom: 8, containLabel: true },
    categoryAxis: {
      axisLine: { lineStyle: { color: BRAND.line } },
      axisTick: { show: false },
      axisLabel: { color: BRAND.muted },
      splitLine: { show: false }
    },
    valueAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: BRAND.muted },
      splitLine: { lineStyle: { color: BRAND.line } }
    },
    tooltip: {
      backgroundColor: '#ffffff',
      borderColor: BRAND.line,
      borderWidth: 1,
      textStyle: { color: BRAND.ink },
      extraCssText: 'box-shadow:0 6px 20px rgba(45,35,20,.12); border-radius:10px;'
    }
  });
}
