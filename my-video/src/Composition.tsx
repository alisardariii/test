import {
  AbsoluteFill,
  CalculateMetadataFunction,
  Composition,
  Easing,
  Interactive,
  Sequence,
  interpolate,
  useCurrentFrame,
} from "remotion";

type Props = {
  title: string;
  subtitle: string;
};

const calculateMetadata: CalculateMetadataFunction<Props> = () => {
  return {};
};

export const MyComposition = () => {
  return (
    <Composition
      id="MyComp"
      component={Main}
      durationInFrames={150}
      fps={30}
      width={1280}
      height={720}
      defaultProps={{
        title: "Hello Remotion",
        subtitle: "Video built with React",
      }}
      calculateMetadata={calculateMetadata}
    />
  );
};

const Main: React.FC<Props> = ({ title, subtitle }) => {
  return (
    <AbsoluteFill style={{ backgroundColor: "#0b1020" }}>
      <Sequence name="Background">
        <Background />
      </Sequence>
      <AbsoluteFill
        style={{
          justifyContent: "center",
          alignItems: "center",
          fontFamily: "system-ui, sans-serif",
          textAlign: "center",
        }}
      >
        <Sequence name="Title" from={10} layout="none">
          <Title text={title} />
        </Sequence>
        <Sequence name="Subtitle" from={35} layout="none">
          <Subtitle text={subtitle} />
        </Sequence>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

const Background: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <Interactive.Div
      name="Gradient glow"
      style={{
        position: "absolute",
        inset: 0,
        background:
          "radial-gradient(circle at 50% 40%, #3b4cca 0%, #0b1020 60%)",
        opacity: interpolate(frame, [0, 30], [0, 1], {
          extrapolateRight: "clamp",
          extrapolateLeft: "clamp",
        }),
        scale: interpolate(frame, [0, 150], [1, 1.15]),
      }}
    />
  );
};

const Title: React.FC<{ text: string }> = ({ text }) => {
  const frame = useCurrentFrame();

  return (
    <Interactive.Div
      name="Hero title"
      style={{
        color: "white",
        fontSize: 96,
        fontWeight: 800,
        letterSpacing: "-2px",
        opacity: interpolate(frame, [0, 25], [0, 1], {
          extrapolateRight: "clamp",
          extrapolateLeft: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
        translate: interpolate(frame, [0, 25], ["0px 40px", "0px 0px"], {
          extrapolateRight: "clamp",
          extrapolateLeft: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
      }}
    >
      {text}
    </Interactive.Div>
  );
};

const Subtitle: React.FC<{ text: string }> = ({ text }) => {
  const frame = useCurrentFrame();

  return (
    <Interactive.Div
      name="Subtitle"
      style={{
        color: "#9db2ff",
        fontSize: 36,
        fontWeight: 500,
        marginTop: 20,
        opacity: interpolate(frame, [0, 25], [0, 1], {
          extrapolateRight: "clamp",
          extrapolateLeft: "clamp",
          easing: Easing.bezier(0.16, 1, 0.3, 1),
        }),
      }}
    >
      {text}
    </Interactive.Div>
  );
};
