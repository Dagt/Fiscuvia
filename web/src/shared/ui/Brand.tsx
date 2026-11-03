export function Brand({
  size = 'md',
  appearance = 'dark',
}: {
  size?: 'sm' | 'md'
  appearance?: 'light' | 'dark'
}) {
  return (
    <div className={`brand brand-${size} brand-${appearance}`}>
      <svg
        className="brand-mark"
        viewBox="0 0 40 40"
        fill="none"
        aria-hidden="true"
      >
        <rect
          width="40"
          height="40"
          rx="10"
          fill="currentColor"
          opacity=".13"
        />
        <path
          d="M12 29V11h17M12 20h13M19 29V11"
          stroke="currentColor"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span>
        Fiscuvia<span className="brand-dot">.</span>
      </span>
    </div>
  )
}
